package com.grash.service;

import com.grash.event.WorkOrderCompletedEvent;
import com.grash.model.Labor;
import com.grash.model.ServiceContract;
import com.grash.model.User;
import com.grash.model.WorkOrder;
import com.grash.repository.ServiceContractRepository;
import com.grash.repository.WorkOrderSlaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceContractConsumptionServiceTest {

    private static final double DELTA = 1e-9;

    @Mock
    private WorkOrderSlaRepository workOrderRepository;
    @Mock
    private ServiceContractRepository serviceContractRepository;
    @Mock
    private LaborService laborService;
    @InjectMocks
    private ServiceContractConsumptionService service;

    private ServiceContract contract;
    private WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        contract = new ServiceContract();
        contract.setId(3L);

        workOrder = new WorkOrder();
        workOrder.setId(12L);
        workOrder.setServiceContract(contract);
    }

    private void stubContractAndLabors(Labor... labors) {
        when(serviceContractRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(contract));
        when(laborService.findByWorkOrder(12L)).thenReturn(Arrays.asList(labors));
    }

    private static Labor labor(double hours, long hourlyRate) {
        Labor labor = new Labor();
        labor.setDuration((long) (hours * 3600));
        labor.setHourlyRate(hourlyRate);
        labor.setIncludeToTotalTime(true);
        return labor;
    }

    // ------------------------------------------------------------ included hours

    @Test
    void includedHours_partiallyCoveredLeavesTheExcessBillable() {
        contract.setIncludedHours(10);
        contract.setRemainingHours(5);
        stubContractAndLabors(labor(2, 50), labor(4, 50));

        assertTrue(service.applyConsumption(workOrder));

        assertEquals(5, workOrder.getContractCoveredHours(), DELTA);
        assertEquals(0, contract.getRemainingHours(), DELTA);
        assertTrue(workOrder.isContractHoursApplied());
        verify(serviceContractRepository).save(contract);
        verify(workOrderRepository).save(workOrder);
    }

    @Test
    void includedHours_fullyCovered() {
        contract.setIncludedHours(10);
        contract.setRemainingHours(10);
        stubContractAndLabors(labor(3, 50));

        service.applyConsumption(workOrder);

        assertEquals(3, workOrder.getContractCoveredHours(), DELTA);
        assertEquals(7, contract.getRemainingHours(), DELTA);
    }

    @Test
    void includedHours_ignoresLaborExcludedFromTotalTime() {
        contract.setIncludedHours(10);
        contract.setRemainingHours(10);
        Labor excluded = labor(5, 50);
        excluded.setIncludeToTotalTime(false);
        stubContractAndLabors(labor(1, 50), excluded);

        service.applyConsumption(workOrder);

        assertEquals(1, workOrder.getContractCoveredHours(), DELTA);
        assertEquals(9, contract.getRemainingHours(), DELTA);
    }

    @Test
    void includedHours_neverGoesNegative() {
        contract.setIncludedHours(10);
        contract.setRemainingHours(-2);
        stubContractAndLabors(labor(3, 50));

        service.applyConsumption(workOrder);

        assertEquals(0, workOrder.getContractCoveredHours(), DELTA);
        assertEquals(0, contract.getRemainingHours(), DELTA);
    }

    @Test
    void includedHours_isIdempotent() {
        contract.setIncludedHours(10);
        contract.setRemainingHours(10);
        stubContractAndLabors(labor(3, 50));

        assertTrue(service.applyConsumption(workOrder));
        assertFalse(service.applyConsumption(workOrder));

        assertEquals(7, contract.getRemainingHours(), DELTA);
        assertEquals(3, workOrder.getContractCoveredHours(), DELTA);
        verify(serviceContractRepository).findByIdForUpdate(3L); // second call did not even load the contract
    }

    @Test
    void alreadyAppliedWorkOrderIsSkipped() {
        workOrder.setContractHoursApplied(true);
        workOrder.setContractCoveredHours(4);

        assertFalse(service.applyConsumption(workOrder));

        assertEquals(4, workOrder.getContractCoveredHours(), DELTA);
        verifyNoInteractions(serviceContractRepository, laborService, workOrderRepository);
    }

    @Test
    void workOrderWithoutContractIsSkipped() {
        workOrder.setServiceContract(null);

        assertFalse(service.applyConsumption(workOrder));

        assertFalse(workOrder.isContractHoursApplied());
        verifyNoInteractions(serviceContractRepository, laborService);
    }

    // ------------------------------------------------------------ retainer

    @Test
    void retainer_deductsHoursTimesRateUsingTheContractOverride() {
        contract.setRetainerBalance(500);
        contract.setHourlyRateOverride(100.0);
        stubContractAndLabors(labor(3, 40));

        service.applyConsumption(workOrder);

        assertEquals(200, contract.getRetainerBalance(), DELTA);
        assertEquals(3, workOrder.getContractCoveredHours(), DELTA);
        assertTrue(workOrder.isContractHoursApplied());
        verify(serviceContractRepository).save(contract);
    }

    @Test
    void retainer_exhaustedBalanceCoversOnlyPartOfTheHours() {
        contract.setRetainerBalance(150);
        contract.setHourlyRateOverride(100.0);
        stubContractAndLabors(labor(3, 40));

        service.applyConsumption(workOrder);

        assertEquals(0, contract.getRetainerBalance(), DELTA);
        assertEquals(1.5, workOrder.getContractCoveredHours(), DELTA);
    }

    @Test
    void retainer_usesTheBillableRateOfTheTechnicianWithoutOverride() {
        contract.setRetainerBalance(1000);
        User technician = new User();
        technician.setBillableHourlyRate(80);
        Labor labor = labor(2, 40);
        labor.setAssignedTo(technician);
        stubContractAndLabors(labor);

        service.applyConsumption(workOrder);

        assertEquals(840, contract.getRetainerBalance(), DELTA);
        assertEquals(2, workOrder.getContractCoveredHours(), DELTA);
    }

    @Test
    void retainer_fallsBackToTheLaborRate() {
        contract.setRetainerBalance(1000);
        stubContractAndLabors(labor(2, 40));

        service.applyConsumption(workOrder);

        assertEquals(920, contract.getRetainerBalance(), DELTA);
        assertEquals(2, workOrder.getContractCoveredHours(), DELTA);
    }

    @Test
    void retainer_laborWithoutAnyRateIsNotCovered() {
        contract.setRetainerBalance(1000);
        stubContractAndLabors(labor(2, 0));

        service.applyConsumption(workOrder);

        assertEquals(1000, contract.getRetainerBalance(), DELTA);
        assertEquals(0, workOrder.getContractCoveredHours(), DELTA);
    }

    @Test
    void retainer_isIdempotent() {
        contract.setRetainerBalance(500);
        contract.setHourlyRateOverride(100.0);
        stubContractAndLabors(labor(3, 40));

        service.applyConsumption(workOrder);
        service.applyConsumption(workOrder);

        assertEquals(200, contract.getRetainerBalance(), DELTA);
    }

    @Test
    void includedHoursTakePrecedenceOverTheRetainer() {
        contract.setIncludedHours(10);
        contract.setRemainingHours(10);
        contract.setRetainerBalance(500);
        stubContractAndLabors(labor(2, 50));

        service.applyConsumption(workOrder);

        assertEquals(8, contract.getRemainingHours(), DELTA);
        assertEquals(500, contract.getRetainerBalance(), DELTA);
    }

    @Test
    void contractWithoutHoursNorRetainerCoversNothingButMarksTheWorkOrder() {
        stubContractAndLabors(labor(2, 50));

        assertTrue(service.applyConsumption(workOrder));

        assertEquals(0, workOrder.getContractCoveredHours(), DELTA);
        assertTrue(workOrder.isContractHoursApplied());
        verify(serviceContractRepository, never()).save(any());
    }

    // ------------------------------------------------------------ listener

    @Test
    void listener_appliesTheConsumptionOfTheCompletedWorkOrder() {
        contract.setIncludedHours(10);
        contract.setRemainingHours(10);
        when(workOrderRepository.findById(12L)).thenReturn(Optional.of(workOrder));
        stubContractAndLabors(labor(1, 50));

        service.onWorkOrderCompleted(new WorkOrderCompletedEvent(1L, 12L, "t", null, null));

        assertEquals(9, contract.getRemainingHours(), DELTA);
        assertTrue(workOrder.isContractHoursApplied());
    }

    @Test
    void listener_neverPropagatesFailures() {
        when(workOrderRepository.findById(12L)).thenThrow(new IllegalStateException("db down"));

        service.onWorkOrderCompleted(new WorkOrderCompletedEvent(1L, 12L, "t", null, null));

        verifyNoInteractions(serviceContractRepository);
    }

    @Test
    void emptyLaborListCoversNothing() {
        contract.setIncludedHours(10);
        contract.setRemainingHours(10);
        when(serviceContractRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(contract));
        when(laborService.findByWorkOrder(12L)).thenReturn(Collections.emptyList());

        service.applyConsumption(workOrder);

        assertEquals(10, contract.getRemainingHours(), DELTA);
        assertEquals(0, workOrder.getContractCoveredHours(), DELTA);
    }
}
