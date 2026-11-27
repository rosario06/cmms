package com.grash.service;

import com.grash.event.WorkOrderCompletedEvent;
import com.grash.model.Customer;
import com.grash.model.Holiday;
import com.grash.model.ServiceContract;
import com.grash.model.SlaPolicy;
import com.grash.model.User;
import com.grash.model.WorkOrder;
import com.grash.model.WorkSchedule;
import com.grash.model.enums.ContractStatus;
import com.grash.model.enums.Priority;
import com.grash.model.enums.SlaStatus;
import com.grash.model.enums.Status;
import com.grash.repository.HolidayRepository;
import com.grash.repository.ServiceContractRepository;
import com.grash.repository.SlaPolicyRepository;
import com.grash.repository.WorkOrderSlaRepository;
import com.grash.repository.WorkScheduleRepository;
import com.grash.utils.BusinessHoursCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Assigns the SLA policy of a work order, computes its deadlines and keeps its {@link SlaStatus} up to date.
 * <p>
 * Deadlines are minutes added to the creation date of the work order (createdAt). By default they are wall clock
 * minutes. When the policy has useBusinessHours they only count the business hours of a work schedule, resolved in
 * this order: schedule of the policy, schedule of the primary user of the work order, default schedule of the
 * company; inactive schedules are skipped and, when none exists, wall clock minutes are used (INFO log). Holidays of
 * the company (and of that schedule) are skipped.
 * <p>
 * The deadlines are stored: a later change of a schedule or of a holiday does NOT recalculate the open work orders
 * (they are recalculated when their priority, contract or primary user changes).
 * <p>
 * AT_RISK uses the calendar time elapsed over the calendar time between createdAt and the due date (80%), also for
 * business hours deadlines (the due date is already expressed in business time, so the ratio is monotonic and
 * consistent).
 * <p>
 * This bean only depends on repositories (not on WorkOrderService) so that WorkOrderService can call it without a
 * dependency cycle.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlaTrackingService {

    private static final int UPDATE_BATCH_SIZE = 500;

    private final SlaPolicyRepository slaPolicyRepository;
    private final ServiceContractRepository serviceContractRepository;
    private final WorkOrderSlaRepository workOrderSlaRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final HolidayRepository holidayRepository;

    /**
     * Current time; overridable in tests (no global Clock bean exists).
     */
    Date now() {
        return new Date();
    }

    /**
     * Resolves the service contract (when the work order has none), selects the SLA policy and computes the
     * deadlines and status of the work order. Call it on a managed work order once created and whenever its
     * priority or contract changes. The work order is saved.
     */
    // noRollbackFor: callers swallow SLA failures, which must not mark the caller's transaction rollback-only
    @Transactional(noRollbackFor = RuntimeException.class)
    public void applySla(WorkOrder workOrder) {
        if (workOrder == null || workOrder.getCompany() == null || workOrder.getCompany().getId() == null) return;
        Long companyId = workOrder.getCompany().getId();
        Date now = now();

        if (workOrder.getServiceContract() == null) {
            resolveContract(workOrder, companyId, now).ifPresent(workOrder::setServiceContract);
        }

        Optional<SlaPolicy> policy = selectPolicy(workOrder, companyId);
        if (policy.isEmpty()) {
            workOrder.setSlaPolicy(null);
            workOrder.setSlaFirstResponseDueAt(null);
            workOrder.setSlaResolutionDueAt(null);
            workOrder.setSlaStatus(null);
        } else {
            SlaPolicy sla = policy.get();
            Date createdAt = workOrder.getCreatedAt() == null ? now : workOrder.getCreatedAt();
            workOrder.setSlaPolicy(sla);
            Optional<WorkSchedule> schedule = sla.isUseBusinessHours() ?
                    resolveSchedule(sla, workOrder, companyId) : Optional.empty();
            Collection<Holiday> holidays = schedule.isPresent() ?
                    holidayRepository.findByCompany_Id(companyId) : Collections.emptyList();
            workOrder.setSlaFirstResponseDueAt(addMinutes(createdAt, sla.getFirstResponseMinutes(),
                    schedule.orElse(null), holidays));
            workOrder.setSlaResolutionDueAt(addMinutes(createdAt, sla.getResolutionMinutes(),
                    schedule.orElse(null), holidays));
            workOrder.setSlaStatus(Status.COMPLETE.equals(workOrder.getStatus()) ? evaluateCompleted(workOrder, now) :
                    evaluate(createdAt, workOrder.getSlaFirstResponseDueAt(), workOrder.getSlaResolutionDueAt(),
                            workOrder.getFirstRespondedAt(), now));
        }
        workOrderSlaRepository.save(workOrder);
    }

    /**
     * Sets firstRespondedAt (only the first time) on the given work order WITHOUT saving it: the caller is already
     * saving it (e.g. WorkOrderService.changeStatus).
     *
     * @return true when the work order was modified
     */
    public boolean markFirstResponse(WorkOrder workOrder) {
        if (workOrder == null || workOrder.getFirstRespondedAt() != null) return false;
        Date now = now();
        workOrder.setFirstRespondedAt(now);
        if (hasSla(workOrder) && !Status.COMPLETE.equals(workOrder.getStatus())) {
            Date createdAt = workOrder.getCreatedAt() == null ? now : workOrder.getCreatedAt();
            workOrder.setSlaStatus(evaluate(createdAt, workOrder.getSlaFirstResponseDueAt(),
                    workOrder.getSlaResolutionDueAt(), now, now));
        }
        return true;
    }

    /**
     * Same as {@link #markFirstResponse(WorkOrder)} but loads and saves the work order (used on check-in).
     */
    @Transactional
    public void markFirstResponse(Long workOrderId) {
        if (workOrderId == null) return;
        workOrderSlaRepository.findById(workOrderId).ifPresent(workOrder -> {
            if (markFirstResponse(workOrder)) workOrderSlaRepository.save(workOrder);
        });
    }

    /**
     * Closes the SLA of a completed work order: MET or BREACHED. Runs in the transaction that completed the work
     * order, before the contract consumption / financial listeners.
     */
    @EventListener
    @Order(1)
    @Transactional
    public void onWorkOrderCompleted(WorkOrderCompletedEvent event) {
        try {
            workOrderSlaRepository.findById(event.getWorkOrderId()).ifPresent(workOrder -> {
                if (!hasSla(workOrder)) return;
                workOrder.setSlaStatus(evaluateCompleted(workOrder, now()));
                workOrderSlaRepository.save(workOrder);
            });
        } catch (RuntimeException e) {
            log.warn("Could not close the SLA of work order {}: {}", event.getWorkOrderId(),
                    e.getClass().getSimpleName());
        }
    }

    /**
     * Recalculates the SLA status of the open work orders every 5 minutes. Uses a projection plus bulk updates, so
     * no entity (nor lazy association) is loaded.
     */
    @Scheduled(fixedRate = 300000, initialDelay = 60000)
    @Transactional
    public void refreshOpenWorkOrders() {
        try {
            Date now = now();
            Map<SlaStatus, List<Long>> idsByStatus = new EnumMap<>(SlaStatus.class);
            for (Object[] row : workOrderSlaRepository.findOpenWithSla(Status.COMPLETE)) {
                Long id = (Long) row[0];
                Date createdAt = (Date) row[1];
                SlaStatus current = (SlaStatus) row[5];
                SlaStatus computed = evaluate(createdAt == null ? now : createdAt, (Date) row[2], (Date) row[3],
                        (Date) row[4], now);
                if (computed != current) idsByStatus.computeIfAbsent(computed, k -> new ArrayList<>()).add(id);
            }
            idsByStatus.forEach((status, ids) -> {
                for (int i = 0; i < ids.size(); i += UPDATE_BATCH_SIZE) {
                    workOrderSlaRepository.updateSlaStatus(ids.subList(i, Math.min(ids.size(),
                            i + UPDATE_BATCH_SIZE)), status);
                }
            });
        } catch (RuntimeException e) {
            log.error("SLA status refresh failed", e);
        }
    }

    // ---------------------------------------------------------------- selection

    Optional<ServiceContract> resolveContract(WorkOrder workOrder, Long companyId, Date now) {
        List<Customer> customers = workOrder.getCustomers();
        if (customers == null || customers.isEmpty()) return Optional.empty();
        List<Long> customerIds = customers.stream().map(Customer::getId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        if (customerIds.isEmpty()) return Optional.empty();
        return serviceContractRepository.findInForceByCustomers(companyId, customerIds, ContractStatus.ACTIVE, now)
                .stream().findFirst();
    }

    /**
     * Policy of the contract with the priority of the work order, otherwise the general policy of the company with
     * that priority. A NONE priority only matches policies whose priority is NONE.
     */
    Optional<SlaPolicy> selectPolicy(WorkOrder workOrder, Long companyId) {
        Priority priority = workOrder.getPriority() == null ? Priority.NONE : workOrder.getPriority();
        ServiceContract contract = workOrder.getServiceContract();
        if (contract != null && contract.getId() != null) {
            Optional<SlaPolicy> contractPolicy =
                    slaPolicyRepository.findFirstByServiceContract_IdAndPriorityOrderByIdAsc(contract.getId(),
                            priority);
            if (contractPolicy.isPresent()) return contractPolicy;
        }
        return slaPolicyRepository.findFirstByCompany_IdAndServiceContractIsNullAndPriorityOrderByIdAsc(companyId,
                priority);
    }

    /**
     * Schedule used by a business hours policy: policy, then primary user of the work order, then company default.
     * Inactive schedules are skipped. Empty (wall clock minutes) when there is none.
     */
    Optional<WorkSchedule> resolveSchedule(SlaPolicy policy, WorkOrder workOrder, Long companyId) {
        WorkSchedule policySchedule = policy.getWorkSchedule();
        if (policySchedule != null && policySchedule.isActive()) return Optional.of(policySchedule);
        User primaryUser = workOrder.getPrimaryUser();
        WorkSchedule userSchedule = primaryUser == null ? null : primaryUser.getWorkSchedule();
        if (userSchedule != null && userSchedule.isActive()) return Optional.of(userSchedule);
        Optional<WorkSchedule> companyDefault =
                workScheduleRepository.findFirstByCompany_IdAndDefaultScheduleTrueAndActiveTrueOrderByIdAsc(companyId);
        if (companyDefault.isEmpty())
            log.info("SLA policy {} uses business hours but no work schedule applies to work order {}: using wall " +
                    "clock minutes", policy.getId(), workOrder.getId());
        return companyDefault;
    }

    // ---------------------------------------------------------------- pure calculations

    static Date addMinutes(Date start, int minutes) {
        if (minutes <= 0) return null;
        return new Date(start.getTime() + minutes * 60_000L);
    }

    /**
     * Business hours variant: a null schedule means wall clock minutes.
     */
    static Date addMinutes(Date start, int minutes, WorkSchedule schedule, Collection<Holiday> holidays) {
        if (schedule == null) return addMinutes(start, minutes);
        if (minutes <= 0) return null;
        Instant due = BusinessHoursCalculator.addBusinessMinutes(Instant.ofEpochMilli(start.getTime()), minutes,
                schedule, holidays);
        return new Date(due.toEpochMilli());
    }

    static boolean hasSla(WorkOrder workOrder) {
        return workOrder.getSlaResolutionDueAt() != null || workOrder.getSlaFirstResponseDueAt() != null;
    }

    /**
     * Status of an open work order: BREACHED when the resolution deadline passed, or the first response deadline
     * passed without response (or the response was late); AT_RISK when at least 80% of a pending deadline window
     * elapsed; ON_TRACK otherwise.
     */
    static SlaStatus evaluate(Date createdAt, Date firstResponseDue, Date resolutionDue, Date firstRespondedAt,
                              Date now) {
        long n = now.getTime();
        if (resolutionDue != null && n > resolutionDue.getTime()) return SlaStatus.BREACHED;
        if (firstResponseDue != null) {
            if (firstRespondedAt == null && n > firstResponseDue.getTime()) return SlaStatus.BREACHED;
            if (firstRespondedAt != null && firstRespondedAt.after(firstResponseDue)) return SlaStatus.BREACHED;
        }
        if (isAtRisk(createdAt, resolutionDue, n)) return SlaStatus.AT_RISK;
        if (firstRespondedAt == null && isAtRisk(createdAt, firstResponseDue, n)) return SlaStatus.AT_RISK;
        return SlaStatus.ON_TRACK;
    }

    private static boolean isAtRisk(Date start, Date due, long now) {
        if (due == null) return false;
        long total = due.getTime() - start.getTime();
        if (total <= 0) return true;
        // elapsed >= 80% of the window, in exact integer arithmetic (elapsed / total >= 4 / 5)
        return (now - start.getTime()) * 5 >= total * 4;
    }

    /**
     * MET when completed within the resolution deadline and the first response was on time (completing the work
     * order counts as the response when no response was recorded), BREACHED otherwise.
     */
    static SlaStatus evaluateCompleted(WorkOrder workOrder, Date now) {
        Date completedOn = workOrder.getCompletedOn() == null ? now : workOrder.getCompletedOn();
        Date resolutionDue = workOrder.getSlaResolutionDueAt();
        Date firstResponseDue = workOrder.getSlaFirstResponseDueAt();
        Date response = workOrder.getFirstRespondedAt() == null ? completedOn : workOrder.getFirstRespondedAt();
        boolean resolvedInTime = resolutionDue == null || !completedOn.after(resolutionDue);
        boolean respondedInTime = firstResponseDue == null || !response.after(firstResponseDue);
        return resolvedInTime && respondedInTime ? SlaStatus.MET : SlaStatus.BREACHED;
    }
}
