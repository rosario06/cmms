package com.grash.service;

import com.grash.event.WorkOrderCompletedEvent;
import com.grash.model.Customer;
import com.grash.model.ServiceContract;
import com.grash.model.SlaPolicy;
import com.grash.model.WorkOrder;
import com.grash.model.enums.ContractStatus;
import com.grash.model.enums.Priority;
import com.grash.model.enums.SlaStatus;
import com.grash.model.enums.Status;
import com.grash.repository.ServiceContractRepository;
import com.grash.repository.SlaPolicyRepository;
import com.grash.repository.WorkOrderSlaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Assigns the SLA policy of a work order, computes its deadlines and keeps its {@link SlaStatus} up to date.
 * <p>
 * Deadlines are wall clock minutes added to the creation date of the work order (createdAt): business hours,
 * weekends and holidays are NOT taken into account.
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
            workOrder.setSlaFirstResponseDueAt(addMinutes(createdAt, sla.getFirstResponseMinutes()));
            workOrder.setSlaResolutionDueAt(addMinutes(createdAt, sla.getResolutionMinutes()));
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

    // ---------------------------------------------------------------- pure calculations

    static Date addMinutes(Date start, int minutes) {
        if (minutes <= 0) return null;
        return new Date(start.getTime() + minutes * 60_000L);
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
