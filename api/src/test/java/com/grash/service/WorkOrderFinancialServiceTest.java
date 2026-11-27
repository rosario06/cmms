package com.grash.service;

import com.grash.dto.WorkOrderFinancialsDTO;
import com.grash.event.CostCenterBudgetAlertEvent;
import com.grash.model.AdditionalCost;
import com.grash.model.Company;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkOrderFinancialServiceTest {

    private static final double DELTA = 0.001;

    @Mock
    private WorkOrderRepository workOrderRepository;
    @Mock
    private LaborRepository laborRepository;
    @Mock
    private PartQuantityRepository partQuantityRepository;
    @Mock
    private AdditionalCostRepository additionalCostRepository;
    @Mock
    private CostCenterRepository costCenterRepository;
    @Mock
    private WorkOrderFinancialRepository workOrderFinancialRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private WorkOrderFinancialService service;
    private Company company;
    private WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        service = new WorkOrderFinancialService(workOrderRepository, laborRepository, partQuantityRepository,
                additionalCostRepository, costCenterRepository, workOrderFinancialRepository, eventPublisher);
        company = new Company();
        company.setId(1L);
        workOrder = new WorkOrder();
        workOrder.setId(10L);
        workOrder.setCompany(company);
        when(workOrderRepository.findById(10L)).thenReturn(Optional.of(workOrder));
        when(laborRepository.findByWorkOrder_Id(10L)).thenReturn(Collections.emptyList());
        when(partQuantityRepository.findByWorkOrder_Id(10L)).thenReturn(Collections.emptyList());
        when(additionalCostRepository.findByWorkOrder_Id(10L)).thenReturn(Collections.emptyList());
        when(costCenterRepository.findSpentComparisonByCompany(1L)).thenReturn(Collections.emptyList());
    }

    private User user(double internalCost, double billableRate) {
        User user = new User();
        user.setId(5L);
        user.setInternalHourlyCost(internalCost);
        user.setBillableHourlyRate(billableRate);
        return user;
    }

    private Labor labor(User user, long seconds, long hourlyRate, boolean includeToTotalTime) {
        Labor labor = new Labor();
        labor.setAssignedTo(user);
        labor.setDuration(seconds);
        labor.setHourlyRate(hourlyRate);
        labor.setIncludeToTotalTime(includeToTotalTime);
        return labor;
    }

    private PartQuantity partQuantity(double cost, double billablePrice, double quantity) {
        Part part = new Part();
        part.setCost(cost);
        part.setBillablePrice(billablePrice);
        return new PartQuantity(part, workOrder, null, quantity);
    }

    private AdditionalCost additionalCost(double cost, boolean include) {
        AdditionalCost additionalCost = new AdditionalCost();
        additionalCost.setCost(cost);
        additionalCost.setIncludeToTotalCost(include);
        return additionalCost;
    }

    // ---------- labor rates ----------

    @Test
    void labor_withoutUserRates_fallsBackToLaborHourlyRate() {
        when(laborRepository.findByWorkOrder_Id(10L))
                .thenReturn(List.of(labor(user(0, 0), 7200, 50, true)));

        WorkOrderFinancialsDTO result = service.calculate(workOrder);

        assertEquals(100, result.getLaborInternal(), DELTA);
        assertEquals(100, result.getLaborBillable(), DELTA);
        assertEquals(0, result.getNetMarginAmount(), DELTA);
        assertEquals(0, result.getMarginPercentage(), DELTA);
    }

    @Test
    void labor_withoutAssignedUser_usesLaborHourlyRate() {
        when(laborRepository.findByWorkOrder_Id(10L)).thenReturn(List.of(labor(null, 3600, 40, true)));

        WorkOrderFinancialsDTO result = service.calculate(workOrder);

        assertEquals(40, result.getLaborInternal(), DELTA);
        assertEquals(40, result.getLaborBillable(), DELTA);
    }

    @Test
    void labor_withUserRates_usesInternalCostAndBillableRate() {
        when(laborRepository.findByWorkOrder_Id(10L))
                .thenReturn(List.of(labor(user(30, 80), 7200, 50, true)));

        WorkOrderFinancialsDTO result = service.calculate(workOrder);

        assertEquals(60, result.getLaborInternal(), DELTA);
        assertEquals(160, result.getLaborBillable(), DELTA);
        assertEquals(100, result.getNetMarginAmount(), DELTA);
        assertEquals(62.5, result.getMarginPercentage(), DELTA);
    }

    @Test
    void labor_notIncludedInTotalTime_isIgnored() {
        when(laborRepository.findByWorkOrder_Id(10L))
                .thenReturn(List.of(labor(user(30, 80), 7200, 50, false)));

        WorkOrderFinancialsDTO result = service.calculate(workOrder);

        assertEquals(0, result.getLaborInternal(), DELTA);
        assertEquals(0, result.getLaborBillable(), DELTA);
    }

    // ---------- contract ----------

    @Test
    void contract_rateOverrideMarkupAndCoveredHours() {
        ServiceContract contract = new ServiceContract();
        contract.setHourlyRateOverride(100.0);
        contract.setPartsMarkupPercent(10);
        workOrder.setServiceContract(contract);
        workOrder.setContractCoveredHours(1);
        when(laborRepository.findByWorkOrder_Id(10L))
                .thenReturn(List.of(labor(user(40, 70), 3 * 3600, 20, true)));
        when(partQuantityRepository.findByWorkOrder_Id(10L)).thenReturn(List.of(partQuantity(10, 0, 2)));
        when(additionalCostRepository.findByWorkOrder_Id(10L))
                .thenReturn(List.of(additionalCost(5, true), additionalCost(100, false)));

        WorkOrderFinancialsDTO result = service.calculate(workOrder);

        assertEquals(120, result.getLaborInternal(), DELTA);
        // 3h x 100 (override, not the user's 70) minus 1 covered hour => 2h x 100
        assertEquals(200, result.getLaborBillable(), DELTA);
        assertEquals(20, result.getPartsInternal(), DELTA);
        assertEquals(22, result.getPartsBillable(), DELTA);
        assertEquals(5, result.getAdditionalInternal(), DELTA);
        assertEquals(5, result.getAdditionalBillable(), DELTA);
        assertEquals(145, result.getTotalInternalCost(), DELTA);
        assertEquals(227, result.getTotalBillableAmount(), DELTA);
        assertEquals(82, result.getNetMarginAmount(), DELTA);
        assertEquals(36.12, result.getMarginPercentage(), DELTA);
        assertEquals(1, result.getContractCoveredHours(), DELTA);
    }

    @Test
    void contract_coveredHoursGreaterThanWorked_billableLaborIsZeroNeverNegative() {
        ServiceContract contract = new ServiceContract();
        workOrder.setServiceContract(contract);
        workOrder.setContractCoveredHours(10);
        when(laborRepository.findByWorkOrder_Id(10L))
                .thenReturn(List.of(labor(user(30, 80), 7200, 50, true)));

        WorkOrderFinancialsDTO result = service.calculate(workOrder);

        assertEquals(60, result.getLaborInternal(), DELTA);
        assertEquals(0, result.getLaborBillable(), DELTA);
        assertEquals(-60, result.getNetMarginAmount(), DELTA);
        assertEquals(0, result.getMarginPercentage(), DELTA);
    }

    @Test
    void contract_withoutRateOverride_usesUserBillableRate() {
        ServiceContract contract = new ServiceContract();
        contract.setHourlyRateOverride(null);
        workOrder.setServiceContract(contract);
        when(laborRepository.findByWorkOrder_Id(10L))
                .thenReturn(List.of(labor(user(30, 80), 3600, 50, true)));

        assertEquals(80, service.calculate(workOrder).getLaborBillable(), DELTA);
    }

    // ---------- parts ----------

    @Test
    void parts_billablePriceHasPriorityOverMarkup() {
        ServiceContract contract = new ServiceContract();
        contract.setPartsMarkupPercent(50);
        workOrder.setServiceContract(contract);
        when(partQuantityRepository.findByWorkOrder_Id(10L)).thenReturn(List.of(partQuantity(10, 25, 2)));

        WorkOrderFinancialsDTO result = service.calculate(workOrder);

        assertEquals(20, result.getPartsInternal(), DELTA);
        assertEquals(50, result.getPartsBillable(), DELTA);
    }

    @Test
    void parts_withoutBillablePriceAndWithoutContract_billsAtCost() {
        when(partQuantityRepository.findByWorkOrder_Id(10L)).thenReturn(List.of(partQuantity(10, 0, 3)));

        WorkOrderFinancialsDTO result = service.calculate(workOrder);

        assertEquals(30, result.getPartsInternal(), DELTA);
        assertEquals(30, result.getPartsBillable(), DELTA);
    }

    // ---------- rounding and margin ----------

    @Test
    void rounding_toTwoDecimals() {
        // 1000 s = 0.27777 h x 10 = 2.7777 -> 2.78
        when(laborRepository.findByWorkOrder_Id(10L))
                .thenReturn(List.of(labor(user(10, 10), 1000, 0, true)));

        WorkOrderFinancialsDTO result = service.calculate(workOrder);

        assertEquals(2.78, result.getLaborInternal(), 0.0001);
        assertEquals(2.78, result.getTotalInternalCost(), 0.0001);
    }

    @Test
    void margin_withZeroBillable_isZeroPercent() {
        when(laborRepository.findByWorkOrder_Id(10L))
                .thenReturn(List.of(labor(user(40, 0), 7200, 0, true)));

        WorkOrderFinancialsDTO result = service.calculate(workOrder);

        assertEquals(80, result.getTotalInternalCost(), DELTA);
        assertEquals(0, result.getTotalBillableAmount(), DELTA);
        assertEquals(-80, result.getNetMarginAmount(), DELTA);
        assertEquals(0, result.getMarginPercentage(), DELTA);
    }

    // ---------- recalculate ----------

    @Test
    void recalculate_savesTotalsInTheWorkOrder_andIsIdempotent() {
        when(laborRepository.findByWorkOrder_Id(10L))
                .thenReturn(List.of(labor(user(30, 80), 7200, 50, true)));

        WorkOrderFinancialsDTO first = service.recalculate(10L);
        WorkOrderFinancialsDTO second = service.recalculate(10L);

        assertEquals(first, second);
        assertEquals(60, workOrder.getTotalInternalCost(), DELTA);
        assertEquals(160, workOrder.getTotalBillableAmount(), DELTA);
        assertEquals(100, workOrder.getNetMarginAmount(), DELTA);
        assertEquals(62.5, workOrder.getMarginPercentage(), DELTA);
        // second run finds nothing to change
        verify(workOrderRepository, times(1)).save(workOrder);
    }

    @Test
    void recalculate_unknownWorkOrder_returnsNull() {
        when(workOrderRepository.findById(99L)).thenReturn(Optional.empty());

        assertNull(service.recalculate(99L));
        assertNull(service.recalculate(null));
        verify(workOrderRepository, never()).save(any());
    }

    @Test
    void recalculateQuietly_neverThrows() {
        when(laborRepository.findByWorkOrder_Id(10L)).thenThrow(new IllegalStateException("boom"));

        service.recalculateQuietly(10L);

        verify(workOrderRepository, never()).save(any());
    }

    @Test
    void recalculate_recomputesCurrentCostCenter_andStaleOnes() {
        CostCenter current = costCenter(7L, 1000, 80);
        CostCenter previous = costCenter(8L, 1000, 80);
        workOrder.setCostCenter(current);
        when(costCenterRepository.findById(7L)).thenReturn(Optional.of(current));
        when(costCenterRepository.findById(8L)).thenReturn(Optional.of(previous));
        when(costCenterRepository.sumInternalCost(7L)).thenReturn(300.0);
        when(costCenterRepository.sumInternalCost(8L)).thenReturn(0.0);
        // center 8 still has the amount stored from when the work order belonged to it
        when(costCenterRepository.findSpentComparisonByCompany(1L)).thenReturn(List.of(
                new Object[]{7L, 0.0, 300.0}, new Object[]{8L, 500.0, 0.0}));
        previous.setSpentAmount(500);

        service.recalculate(10L);

        assertEquals(300, current.getSpentAmount(), DELTA);
        assertEquals(0, previous.getSpentAmount(), DELTA);
        verify(costCenterRepository).saveAndFlush(current);
        verify(costCenterRepository).saveAndFlush(previous);
    }

    // ---------- cost center alert ----------

    private CostCenter costCenter(Long id, double budget, double threshold) {
        CostCenter costCenter = new CostCenter();
        costCenter.setId(id);
        costCenter.setCode("CC-" + id);
        costCenter.setName("Center " + id);
        costCenter.setCompany(company);
        costCenter.setAllocatedBudget(budget);
        costCenter.setAlertThresholdPercent(threshold);
        return costCenter;
    }

    @Test
    void costCenter_alertIsFiredOnlyOnce_andRearmedWhenBelowThreshold() {
        CostCenter costCenter = costCenter(7L, 1000, 80);
        when(costCenterRepository.findById(7L)).thenReturn(Optional.of(costCenter));

        when(costCenterRepository.sumInternalCost(7L)).thenReturn(500.0);
        service.recalculateForCostCenter(7L);
        assertFalse(costCenter.isAlertSent());
        verify(eventPublisher, never()).publishEvent(any(Object.class));

        when(costCenterRepository.sumInternalCost(7L)).thenReturn(850.0);
        service.recalculateForCostCenter(7L);
        assertTrue(costCenter.isAlertSent());
        assertEquals(850, costCenter.getSpentAmount(), DELTA);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        CostCenterBudgetAlertEvent event = (CostCenterBudgetAlertEvent) captor.getValue();
        assertEquals(7L, event.getCostCenterId());
        assertEquals(85, event.getExecutionPercent(), DELTA);

        // still above the threshold: no second alert
        when(costCenterRepository.sumInternalCost(7L)).thenReturn(950.0);
        service.recalculateForCostCenter(7L);
        service.recalculateForCostCenter(7L);
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
        assertTrue(costCenter.isAlertSent());

        // goes back below the threshold: re-armed, and fires again on the next crossing
        when(costCenterRepository.sumInternalCost(7L)).thenReturn(100.0);
        service.recalculateForCostCenter(7L);
        assertFalse(costCenter.isAlertSent());
        when(costCenterRepository.sumInternalCost(7L)).thenReturn(900.0);
        service.recalculateForCostCenter(7L);
        verify(eventPublisher, times(2)).publishEvent(any(Object.class));
    }

    @Test
    void costCenter_withoutBudget_neverAlerts() {
        CostCenter costCenter = costCenter(7L, 0, 80);
        when(costCenterRepository.findById(7L)).thenReturn(Optional.of(costCenter));
        when(costCenterRepository.sumInternalCost(7L)).thenReturn(500.0);

        service.recalculateForCostCenter(7L);

        assertFalse(costCenter.isAlertSent());
        assertEquals(500, costCenter.getSpentAmount(), DELTA);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void costCenter_unknown_isIgnored() {
        when(costCenterRepository.findById(anyLong())).thenReturn(Optional.empty());

        service.recalculateForCostCenter(404L);

        assertNotNull(service);
        verify(costCenterRepository, never()).saveAndFlush(any());
    }
}
