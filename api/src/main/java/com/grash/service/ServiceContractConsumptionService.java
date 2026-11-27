package com.grash.service;

import com.grash.event.WorkOrderCompletedEvent;
import com.grash.model.Labor;
import com.grash.model.ServiceContract;
import com.grash.model.WorkOrder;
import com.grash.repository.ServiceContractRepository;
import com.grash.repository.WorkOrderSlaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;

/**
 * Deducts the labor hours of a completed work order from its service contract (included hours, or retainer balance
 * when the contract has no included hours). Runs synchronously inside the transaction that completes the work
 * order, with {@code @Order(1)}: the financial listener ({@code @Order(2)}) reads WorkOrder.contractCoveredHours and
 * treats the remaining hours as billable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceContractConsumptionService {
    private static final double SECONDS_PER_HOUR = 3600.0;

    private final WorkOrderSlaRepository workOrderRepository;
    private final ServiceContractRepository serviceContractRepository;
    private final LaborService laborService;

    @EventListener
    @Order(1)
    @Transactional
    public void onWorkOrderCompleted(WorkOrderCompletedEvent event) {
        try {
            workOrderRepository.findById(event.getWorkOrderId()).ifPresent(this::applyConsumption);
        } catch (RuntimeException e) {
            log.error("Could not apply the service contract consumption of work order {}", event.getWorkOrderId(),
                    e);
        }
    }

    /**
     * Idempotent: a work order whose contractHoursApplied flag is set is never deducted again.
     *
     * @return true when hours / retainer were processed by this call
     */
    @Transactional
    public boolean applyConsumption(WorkOrder workOrder) {
        if (workOrder == null || workOrder.isContractHoursApplied() || workOrder.getServiceContract() == null
                || workOrder.getServiceContract().getId() == null) return false;
        Optional<ServiceContract> optionalContract =
                serviceContractRepository.findByIdForUpdate(workOrder.getServiceContract().getId());
        if (optionalContract.isEmpty()) return false;
        ServiceContract contract = optionalContract.get();

        Collection<Labor> labors = laborService.findByWorkOrder(workOrder.getId());
        double covered = 0;
        if (contract.getIncludedHours() > 0) {
            double totalHours = labors.stream().filter(Labor::isIncludeToTotalTime)
                    .mapToDouble(labor -> hours(labor)).sum();
            double remaining = Math.max(0, contract.getRemainingHours());
            covered = Math.min(totalHours, remaining);
            contract.setRemainingHours(Math.max(0, remaining - covered));
            serviceContractRepository.save(contract);
        } else if (contract.getRetainerBalance() > 0) {
            double balance = contract.getRetainerBalance();
            for (Labor labor : labors) {
                if (!labor.isIncludeToTotalTime() || balance <= 0) continue;
                double rate = rate(contract, labor);
                double hours = hours(labor);
                if (rate <= 0 || hours <= 0) continue;
                double deducted = Math.min(hours * rate, balance);
                balance -= deducted;
                covered += deducted / rate;
            }
            contract.setRetainerBalance(Math.max(0, balance));
            serviceContractRepository.save(contract);
        }
        workOrder.setContractCoveredHours(covered);
        workOrder.setContractHoursApplied(true);
        workOrderRepository.save(workOrder);
        return true;
    }

    private static double hours(Labor labor) {
        return Math.max(0, labor.getDuration()) / SECONDS_PER_HOUR;
    }

    /**
     * hourlyRateOverride of the contract, otherwise billable rate of the technician, otherwise the rate of the
     * labor record.
     */
    static double rate(ServiceContract contract, Labor labor) {
        if (contract.getHourlyRateOverride() != null && contract.getHourlyRateOverride() > 0)
            return contract.getHourlyRateOverride();
        if (labor.getAssignedTo() != null && labor.getAssignedTo().getBillableHourlyRate() > 0)
            return labor.getAssignedTo().getBillableHourlyRate();
        return labor.getHourlyRate();
    }
}
