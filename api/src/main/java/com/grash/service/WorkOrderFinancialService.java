package com.grash.service;

import com.grash.dto.ProfitabilityReportDTO;
import com.grash.dto.WorkOrderFinancialsDTO;
import com.grash.event.CostCenterBudgetAlertEvent;
import com.grash.event.WorkOrderCompletedEvent;
import com.grash.model.AdditionalCost;
import com.grash.model.CostCenter;
import com.grash.model.Labor;
import com.grash.model.Part;
import com.grash.model.PartQuantity;
import com.grash.model.ServiceContract;
import com.grash.model.User;
import com.grash.model.WorkOrder;
import com.grash.repository.AdditionalCostRepository;
import com.grash.repository.CostCenterRepository;
import com.grash.repository.LaborRepository;
import com.grash.repository.PartQuantityRepository;
import com.grash.repository.WorkOrderFinancialRepository;
import com.grash.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Computes cost (internal), billable amount and margin of work orders, and keeps the spent amount of cost centers
 * in sync. Depends only on repositories so that Labor/AdditionalCost/PartQuantity services can call it without
 * bean cycles.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WorkOrderFinancialService {

    public static final int MAX_REPORT_WORK_ORDERS = 500;
    public static final long DEFAULT_REPORT_DAYS = 90;
    private static final double EPSILON = 0.004;

    private final WorkOrderRepository workOrderRepository;
    private final LaborRepository laborRepository;
    private final PartQuantityRepository partQuantityRepository;
    private final AdditionalCostRepository additionalCostRepository;
    private final CostCenterRepository costCenterRepository;
    private final WorkOrderFinancialRepository workOrderFinancialRepository;
    private final ApplicationEventPublisher eventPublisher;

    public static double round2(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0;
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Runs after the contracts listener (@Order(1)) which deducts the covered hours from the contract.
     */
    @EventListener
    @Order(2)
    @Transactional
    public void onWorkOrderCompleted(WorkOrderCompletedEvent event) {
        recalculateQuietly(event.getWorkOrderId());
    }

    /**
     * Same as {@link #recalculate(Long)} but never throws: used as a side effect of other operations (labor, parts,
     * additional costs) which must not fail because of the financial calculation.
     */
    @Transactional
    public void recalculateQuietly(Long workOrderId) {
        try {
            recalculate(workOrderId);
        } catch (Exception e) {
            log.warn("Could not recalculate the financials of work order {}: {}", workOrderId,
                    e.getClass().getSimpleName());
        }
    }

    /**
     * Idempotent: always recomputed from labors, parts and additional costs, never incrementally.
     *
     * @return the breakdown, or null if the work order does not exist
     */
    @Transactional
    public WorkOrderFinancialsDTO recalculate(Long workOrderId) {
        if (workOrderId == null) return null;
        Optional<WorkOrder> optionalWorkOrder = workOrderRepository.findById(workOrderId);
        if (optionalWorkOrder.isEmpty()) return null;
        WorkOrder workOrder = optionalWorkOrder.get();

        WorkOrderFinancialsDTO financials = calculate(workOrder);

        boolean changed = workOrder.getTotalInternalCost() != financials.getTotalInternalCost()
                || workOrder.getTotalBillableAmount() != financials.getTotalBillableAmount()
                || workOrder.getNetMarginAmount() != financials.getNetMarginAmount()
                || workOrder.getMarginPercentage() != financials.getMarginPercentage();
        if (changed) {
            workOrder.setTotalInternalCost(financials.getTotalInternalCost());
            workOrder.setTotalBillableAmount(financials.getTotalBillableAmount());
            workOrder.setNetMarginAmount(financials.getNetMarginAmount());
            workOrder.setMarginPercentage(financials.getMarginPercentage());
            workOrderRepository.save(workOrder);
        }
        syncCostCenters(workOrder);
        return financials;
    }

    /**
     * Pure calculation (nothing is saved).
     */
    @Transactional(readOnly = true)
    public WorkOrderFinancialsDTO calculate(WorkOrder workOrder) {
        ServiceContract contract = workOrder.getServiceContract();
        Double rateOverride = contract == null ? null : contract.getHourlyRateOverride();
        boolean hasRateOverride = rateOverride != null && rateOverride > 0;
        double partsMarkup = contract == null ? 0 : contract.getPartsMarkupPercent();

        // Labor
        double totalHours = 0;
        double laborInternal = 0;
        double laborBillableGross = 0;
        Collection<Labor> labors = laborRepository.findByWorkOrder_Id(workOrder.getId());
        for (Labor labor : labors) {
            if (!labor.isIncludeToTotalTime()) continue;
            double hours = Math.max(labor.getDuration(), 0) / 3600.0;
            User user = labor.getAssignedTo();
            double internalRate = user != null && user.getInternalHourlyCost() > 0 ? user.getInternalHourlyCost()
                    : labor.getHourlyRate();
            double billableRate;
            if (hasRateOverride) billableRate = rateOverride;
            else if (user != null && user.getBillableHourlyRate() > 0) billableRate = user.getBillableHourlyRate();
            else billableRate = labor.getHourlyRate();
            totalHours += hours;
            laborInternal += hours * internalRate;
            laborBillableGross += hours * billableRate;
        }
        double coveredHours = Math.min(Math.max(workOrder.getContractCoveredHours(), 0), totalHours);
        double laborBillable = totalHours > 0
                ? Math.max(0, laborBillableGross * (totalHours - coveredHours) / totalHours) : 0;

        // Parts
        double partsInternal = 0;
        double partsBillable = 0;
        Collection<PartQuantity> partQuantities = partQuantityRepository.findByWorkOrder_Id(workOrder.getId());
        for (PartQuantity partQuantity : partQuantities) {
            Part part = partQuantity.getPart();
            if (part == null) continue;
            double quantity = partQuantity.getQuantity();
            partsInternal += part.getCost() * quantity;
            double unitPrice = part.getBillablePrice() > 0 ? part.getBillablePrice()
                    : part.getCost() * (1 + partsMarkup / 100);
            partsBillable += unitPrice * quantity;
        }

        // Additional costs
        double additional = 0;
        Collection<AdditionalCost> additionalCosts = additionalCostRepository.findByWorkOrder_Id(workOrder.getId());
        for (AdditionalCost additionalCost : additionalCosts) {
            if (additionalCost.isIncludeToTotalCost()) additional += additionalCost.getCost();
        }

        double rLaborInternal = round2(laborInternal);
        double rLaborBillable = round2(laborBillable);
        double rPartsInternal = round2(partsInternal);
        double rPartsBillable = round2(partsBillable);
        double rAdditional = round2(additional);

        double totalInternal = round2(rLaborInternal + rPartsInternal + rAdditional);
        double totalBillable = round2(rLaborBillable + rPartsBillable + rAdditional);
        double net = round2(totalBillable - totalInternal);
        double marginPercentage = totalBillable > 0 ? round2(net / totalBillable * 100) : 0;

        return new WorkOrderFinancialsDTO(rLaborInternal, rLaborBillable, rPartsInternal, rPartsBillable,
                rAdditional, rAdditional, totalInternal, totalBillable, net, marginPercentage,
                workOrder.getContractCoveredHours());
    }

    /**
     * Recomputes the cost center of the work order and any other center of the company whose stored spent amount is
     * stale (for example the previous center when the work order was moved to another one).
     */
    private void syncCostCenters(WorkOrder workOrder) {
        Long currentId = workOrder.getCostCenter() == null ? null : workOrder.getCostCenter().getId();
        if (currentId != null) recalculateForCostCenter(currentId);
        Long companyId = workOrder.getCompany() == null ? null : workOrder.getCompany().getId();
        if (companyId == null) return;
        List<Object[]> rows = costCenterRepository.findSpentComparisonByCompany(companyId);
        if (rows == null) return;
        for (Object[] row : rows) {
            Long id = ((Number) row[0]).longValue();
            if (id.equals(currentId)) continue;
            double stored = ((Number) row[1]).doubleValue();
            double real = round2(((Number) row[2]).doubleValue());
            if (Math.abs(stored - real) > EPSILON) recalculateForCostCenter(id);
        }
    }

    /**
     * Recomputes spentAmount from the database (sum of the internal cost of its work orders) and evaluates the
     * budget alert: fired once when the threshold is reached, re-armed when the amount goes back below it.
     */
    @Transactional
    public void recalculateForCostCenter(Long costCenterId) {
        if (costCenterId == null) return;
        Optional<CostCenter> optionalCostCenter = costCenterRepository.findById(costCenterId);
        if (optionalCostCenter.isEmpty()) return;
        CostCenter costCenter = optionalCostCenter.get();
        Double sum = costCenterRepository.sumInternalCost(costCenterId);
        costCenter.setSpentAmount(round2(sum == null ? 0 : sum));
        evaluateAlert(costCenter);
        costCenterRepository.saveAndFlush(costCenter);
    }

    private void evaluateAlert(CostCenter costCenter) {
        double budget = costCenter.getAllocatedBudget();
        double percent = budget > 0 ? costCenter.getSpentAmount() / budget * 100 : 0;
        boolean overThreshold = budget > 0 && percent >= costCenter.getAlertThresholdPercent();
        if (overThreshold && !costCenter.isAlertSent()) {
            costCenter.setAlertSent(true);
            log.warn("Cost center {} ({}) reached {}% of its budget (threshold {}%)", costCenter.getCode(),
                    costCenter.getId(), round2(percent), costCenter.getAlertThresholdPercent());
            try {
                Long companyId = costCenter.getCompany() == null ? null : costCenter.getCompany().getId();
                eventPublisher.publishEvent(new CostCenterBudgetAlertEvent(companyId, costCenter.getId(),
                        costCenter.getCode(), costCenter.getName(), budget, costCenter.getSpentAmount(),
                        round2(percent), costCenter.getAlertThresholdPercent()));
            } catch (Exception e) {
                log.warn("Could not publish the budget alert of cost center {}: {}", costCenter.getId(),
                        e.getClass().getSimpleName());
            }
        } else if (!overThreshold && costCenter.isAlertSent()) {
            costCenter.setAlertSent(false);
        }
    }

    /**
     * Breakdown computed on demand (consistent with what recalculate would store).
     */
    @Transactional(readOnly = true)
    public WorkOrderFinancialsDTO getFinancials(WorkOrder workOrder) {
        return calculate(workOrder);
    }

    @Transactional(readOnly = true)
    public ProfitabilityReportDTO getProfitability(Long companyId, Date start, Date end, Long customerId) {
        long customerFilter = customerId == null ? -1L : customerId;
        ProfitabilityReportDTO report = new ProfitabilityReportDTO();

        for (Object[] row : workOrderFinancialRepository.profitabilityByCustomer(companyId, start, end,
                customerFilter)) {
            double internal = round2(((Number) row[3]).doubleValue());
            double billable = round2(((Number) row[4]).doubleValue());
            double net = round2(billable - internal);
            report.getByCustomer().add(new ProfitabilityReportDTO.CustomerRow(((Number) row[0]).longValue(),
                    (String) row[1], ((Number) row[2]).longValue(), internal, billable, net,
                    billable > 0 ? round2(net / billable * 100) : 0));
        }

        List<Object[]> workOrderRows = workOrderFinancialRepository.profitabilityByWorkOrder(companyId, start, end,
                customerFilter, PageRequest.of(0, MAX_REPORT_WORK_ORDERS));
        List<Long> ids = new ArrayList<>();
        for (Object[] row : workOrderRows) ids.add(((Number) row[0]).longValue());
        Map<Long, List<String>> customerNames = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : workOrderFinancialRepository.findCustomerNames(ids)) {
                customerNames.computeIfAbsent(((Number) row[0]).longValue(), k -> new ArrayList<>())
                        .add((String) row[1]);
            }
        }
        for (Object[] row : workOrderRows) {
            Long id = ((Number) row[0]).longValue();
            double internal = round2(((Number) row[2]).doubleValue());
            double billable = round2(((Number) row[3]).doubleValue());
            double net = round2(billable - internal);
            List<String> names = customerNames.get(id);
            report.getByWorkOrder().add(new ProfitabilityReportDTO.WorkOrderRow(id, (String) row[1],
                    names == null ? null : String.join(", ", names), internal, billable, net,
                    billable > 0 ? round2(net / billable * 100) : 0));
        }
        return report;
    }
}
