package com.grash.service;

import com.grash.event.WorkOrderCompletedEvent;
import com.grash.model.Company;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaTrackingServiceTest {

    private static final long BASE = 1_740_988_800_000L; // 2025-03-03T08:00:00Z
    private static final long MINUTE = 60_000L;

    @Mock
    private SlaPolicyRepository slaPolicyRepository;
    @Mock
    private ServiceContractRepository serviceContractRepository;
    @Mock
    private WorkOrderSlaRepository workOrderSlaRepository;

    private Date now = new Date(BASE);
    private SlaTrackingService service;
    private Company company;
    private WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        service = new SlaTrackingService(slaPolicyRepository, serviceContractRepository, workOrderSlaRepository) {
            @Override
            Date now() {
                return now;
            }
        };
        company = new Company();
        company.setId(1L);

        workOrder = new WorkOrder();
        workOrder.setId(12L);
        workOrder.setCompany(company);
        workOrder.setPriority(Priority.HIGH);
        workOrder.setStatus(Status.OPEN);
        workOrder.setCreatedAt(new Date(BASE));
    }

    private static Date at(long millis) {
        return new Date(millis);
    }

    private SlaPolicy policy(long id, int firstResponse, int resolution) {
        SlaPolicy policy = new SlaPolicy();
        policy.setId(id);
        policy.setName("policy " + id);
        policy.setFirstResponseMinutes(firstResponse);
        policy.setResolutionMinutes(resolution);
        return policy;
    }

    private ServiceContract contract(long id) {
        ServiceContract contract = new ServiceContract();
        contract.setId(id);
        contract.setStatus(ContractStatus.ACTIVE);
        return contract;
    }

    // ------------------------------------------------------------ policy selection

    @Test
    void applySla_prefersThePolicyOfTheContract() {
        ServiceContract contract = contract(3L);
        workOrder.setServiceContract(contract);
        SlaPolicy contractPolicy = policy(10L, 30, 240);
        when(slaPolicyRepository.findFirstByServiceContract_IdAndPriorityOrderByIdAsc(3L, Priority.HIGH))
                .thenReturn(Optional.of(contractPolicy));

        service.applySla(workOrder);

        assertSame(contractPolicy, workOrder.getSlaPolicy());
        verify(slaPolicyRepository, never())
                .findFirstByCompany_IdAndServiceContractIsNullAndPriorityOrderByIdAsc(anyLong(), any());
        verify(workOrderSlaRepository).save(workOrder);
    }

    @Test
    void applySla_fallsBackToTheGeneralPolicyOfTheCompany() {
        workOrder.setServiceContract(contract(3L));
        SlaPolicy general = policy(20L, 60, 480);
        when(slaPolicyRepository.findFirstByServiceContract_IdAndPriorityOrderByIdAsc(3L, Priority.HIGH))
                .thenReturn(Optional.empty());
        when(slaPolicyRepository.findFirstByCompany_IdAndServiceContractIsNullAndPriorityOrderByIdAsc(1L,
                Priority.HIGH)).thenReturn(Optional.of(general));

        service.applySla(workOrder);

        assertSame(general, workOrder.getSlaPolicy());
    }

    @Test
    void applySla_withoutContractUsesTheGeneralPolicy() {
        SlaPolicy general = policy(20L, 60, 480);
        when(slaPolicyRepository.findFirstByCompany_IdAndServiceContractIsNullAndPriorityOrderByIdAsc(1L,
                Priority.HIGH)).thenReturn(Optional.of(general));

        service.applySla(workOrder);

        assertSame(general, workOrder.getSlaPolicy());
        assertNull(workOrder.getServiceContract());
        verify(slaPolicyRepository, never()).findFirstByServiceContract_IdAndPriorityOrderByIdAsc(anyLong(), any());
    }

    @Test
    void applySla_resolvesTheActiveContractOfTheCustomer() {
        Customer customer = new Customer();
        customer.setId(5L);
        workOrder.setCustomers(new ArrayList<>(Collections.singletonList(customer)));
        ServiceContract contract = contract(3L);
        when(serviceContractRepository.findInForceByCustomers(eq(1L), anyCollection(), eq(ContractStatus.ACTIVE),
                eq(now))).thenReturn(List.of(contract));
        SlaPolicy contractPolicy = policy(10L, 30, 240);
        when(slaPolicyRepository.findFirstByServiceContract_IdAndPriorityOrderByIdAsc(3L, Priority.HIGH))
                .thenReturn(Optional.of(contractPolicy));

        service.applySla(workOrder);

        assertSame(contract, workOrder.getServiceContract());
        assertSame(contractPolicy, workOrder.getSlaPolicy());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(serviceContractRepository).findInForceByCustomers(eq(1L), ids.capture(), eq(ContractStatus.ACTIVE),
                eq(now));
        assertEquals(List.of(5L), List.copyOf(ids.getValue()));
    }

    @Test
    void applySla_keepsTheContractFixedManually() {
        ServiceContract manual = contract(9L);
        workOrder.setServiceContract(manual);
        Customer customer = new Customer();
        customer.setId(5L);
        workOrder.setCustomers(new ArrayList<>(Collections.singletonList(customer)));
        when(slaPolicyRepository.findFirstByServiceContract_IdAndPriorityOrderByIdAsc(9L, Priority.HIGH))
                .thenReturn(Optional.empty());
        when(slaPolicyRepository.findFirstByCompany_IdAndServiceContractIsNullAndPriorityOrderByIdAsc(1L,
                Priority.HIGH)).thenReturn(Optional.empty());

        service.applySla(workOrder);

        assertSame(manual, workOrder.getServiceContract());
        verify(serviceContractRepository, never()).findInForceByCustomers(any(), any(), any(), any());
    }

    @Test
    void applySla_priorityNoneOnlyMatchesNonePolicies() {
        workOrder.setPriority(Priority.NONE);
        when(slaPolicyRepository.findFirstByCompany_IdAndServiceContractIsNullAndPriorityOrderByIdAsc(1L,
                Priority.NONE)).thenReturn(Optional.empty());

        service.applySla(workOrder);

        assertNull(workOrder.getSlaPolicy());
        assertNull(workOrder.getSlaStatus());
        verify(slaPolicyRepository).findFirstByCompany_IdAndServiceContractIsNullAndPriorityOrderByIdAsc(1L,
                Priority.NONE);
    }

    @Test
    void applySla_clearsTheSlaWhenNoPolicyApplies() {
        workOrder.setSlaPolicy(policy(1L, 10, 10));
        workOrder.setSlaFirstResponseDueAt(at(BASE));
        workOrder.setSlaResolutionDueAt(at(BASE));
        workOrder.setSlaStatus(SlaStatus.AT_RISK);
        when(slaPolicyRepository.findFirstByCompany_IdAndServiceContractIsNullAndPriorityOrderByIdAsc(1L,
                Priority.HIGH)).thenReturn(Optional.empty());

        service.applySla(workOrder);

        assertNull(workOrder.getSlaPolicy());
        assertNull(workOrder.getSlaFirstResponseDueAt());
        assertNull(workOrder.getSlaResolutionDueAt());
        assertNull(workOrder.getSlaStatus());
    }

    // ------------------------------------------------------------ deadlines

    @Test
    void applySla_computesDeadlinesFromCreatedAtAndStartsOnTrack() {
        workOrder.setCreatedAt(at(BASE - 10 * MINUTE));
        when(slaPolicyRepository.findFirstByCompany_IdAndServiceContractIsNullAndPriorityOrderByIdAsc(1L,
                Priority.HIGH)).thenReturn(Optional.of(policy(20L, 60, 480)));

        service.applySla(workOrder);

        assertEquals(at(BASE + 50 * MINUTE), workOrder.getSlaFirstResponseDueAt());
        assertEquals(at(BASE + 470 * MINUTE), workOrder.getSlaResolutionDueAt());
        assertEquals(SlaStatus.ON_TRACK, workOrder.getSlaStatus());
    }

    @Test
    void applySla_zeroMinutesMeansNoTarget() {
        when(slaPolicyRepository.findFirstByCompany_IdAndServiceContractIsNullAndPriorityOrderByIdAsc(1L,
                Priority.HIGH)).thenReturn(Optional.of(policy(20L, 0, 120)));

        service.applySla(workOrder);

        assertNull(workOrder.getSlaFirstResponseDueAt());
        assertEquals(at(BASE + 120 * MINUTE), workOrder.getSlaResolutionDueAt());
    }

    // ------------------------------------------------------------ transitions

    @Test
    void evaluate_onTrackBeforeEightyPercent() {
        Date created = at(BASE);
        Date resolutionDue = at(BASE + 100 * MINUTE);
        assertEquals(SlaStatus.ON_TRACK, SlaTrackingService.evaluate(created, null, resolutionDue, null,
                at(BASE + 79 * MINUTE)));
    }

    @Test
    void evaluate_atRiskFromEightyPercentOfTheResolutionWindow() {
        Date created = at(BASE);
        Date resolutionDue = at(BASE + 100 * MINUTE);
        assertEquals(SlaStatus.AT_RISK, SlaTrackingService.evaluate(created, null, resolutionDue, null,
                at(BASE + 80 * MINUTE)));
        assertEquals(SlaStatus.AT_RISK, SlaTrackingService.evaluate(created, null, resolutionDue, null,
                at(BASE + 100 * MINUTE)));
    }

    @Test
    void evaluate_breachedAfterTheResolutionDeadline() {
        assertEquals(SlaStatus.BREACHED, SlaTrackingService.evaluate(at(BASE), null, at(BASE + 100 * MINUTE), null,
                at(BASE + 100 * MINUTE + 1)));
    }

    @Test
    void evaluate_breachedWhenFirstResponseDeadlinePassedWithoutResponse() {
        assertEquals(SlaStatus.BREACHED, SlaTrackingService.evaluate(at(BASE), at(BASE + 30 * MINUTE),
                at(BASE + 480 * MINUTE), null, at(BASE + 31 * MINUTE)));
    }

    @Test
    void evaluate_respondedOnTimeDoesNotBreachNorRiskOnFirstResponse() {
        assertEquals(SlaStatus.ON_TRACK, SlaTrackingService.evaluate(at(BASE), at(BASE + 30 * MINUTE),
                at(BASE + 480 * MINUTE), at(BASE + 10 * MINUTE), at(BASE + 29 * MINUTE)));
    }

    @Test
    void evaluate_lateFirstResponseStaysBreached() {
        assertEquals(SlaStatus.BREACHED, SlaTrackingService.evaluate(at(BASE), at(BASE + 30 * MINUTE),
                at(BASE + 480 * MINUTE), at(BASE + 40 * MINUTE), at(BASE + 50 * MINUTE)));
    }

    @Test
    void evaluate_atRiskForPendingFirstResponse() {
        // 24 of 30 minutes elapsed without response, resolution still far
        assertEquals(SlaStatus.AT_RISK, SlaTrackingService.evaluate(at(BASE), at(BASE + 30 * MINUTE),
                at(BASE + 480 * MINUTE), null, at(BASE + 24 * MINUTE)));
    }

    @Test
    void evaluate_zeroLengthWindowIsAtRisk() {
        assertEquals(SlaStatus.AT_RISK, SlaTrackingService.evaluate(at(BASE), null, at(BASE), null, at(BASE)));
    }

    @Test
    void evaluateCompleted_metWhenWithinDeadlines() {
        workOrder.setSlaFirstResponseDueAt(at(BASE + 30 * MINUTE));
        workOrder.setSlaResolutionDueAt(at(BASE + 240 * MINUTE));
        workOrder.setFirstRespondedAt(at(BASE + 10 * MINUTE));
        workOrder.setCompletedOn(at(BASE + 240 * MINUTE));

        assertEquals(SlaStatus.MET, SlaTrackingService.evaluateCompleted(workOrder, now));
    }

    @Test
    void evaluateCompleted_breachedWhenCompletedLate() {
        workOrder.setSlaResolutionDueAt(at(BASE + 240 * MINUTE));
        workOrder.setCompletedOn(at(BASE + 241 * MINUTE));

        assertEquals(SlaStatus.BREACHED, SlaTrackingService.evaluateCompleted(workOrder, now));
    }

    @Test
    void evaluateCompleted_breachedWhenFirstResponseWasLate() {
        workOrder.setSlaFirstResponseDueAt(at(BASE + 30 * MINUTE));
        workOrder.setSlaResolutionDueAt(at(BASE + 240 * MINUTE));
        workOrder.setFirstRespondedAt(at(BASE + 31 * MINUTE));
        workOrder.setCompletedOn(at(BASE + 100 * MINUTE));

        assertEquals(SlaStatus.BREACHED, SlaTrackingService.evaluateCompleted(workOrder, now));
    }

    @Test
    void evaluateCompleted_completionCountsAsResponseWhenNoneWasRecorded() {
        workOrder.setSlaFirstResponseDueAt(at(BASE + 30 * MINUTE));
        workOrder.setSlaResolutionDueAt(at(BASE + 240 * MINUTE));
        workOrder.setCompletedOn(at(BASE + 20 * MINUTE));

        assertEquals(SlaStatus.MET, SlaTrackingService.evaluateCompleted(workOrder, now));

        workOrder.setCompletedOn(at(BASE + 35 * MINUTE));
        assertEquals(SlaStatus.BREACHED, SlaTrackingService.evaluateCompleted(workOrder, now));
    }

    // ------------------------------------------------------------ first response

    @Test
    void markFirstResponse_setsItOnlyTheFirstTime() {
        workOrder.setSlaFirstResponseDueAt(at(BASE + 30 * MINUTE));
        workOrder.setSlaResolutionDueAt(at(BASE + 240 * MINUTE));
        workOrder.setSlaStatus(SlaStatus.AT_RISK);

        assertTrue(service.markFirstResponse(workOrder));
        assertEquals(now, workOrder.getFirstRespondedAt());
        assertEquals(SlaStatus.ON_TRACK, workOrder.getSlaStatus());

        now = at(BASE + 5 * MINUTE);
        assertFalse(service.markFirstResponse(workOrder));
        assertEquals(at(BASE), workOrder.getFirstRespondedAt());
    }

    @Test
    void markFirstResponse_withoutSlaDoesNotInventAStatus() {
        assertTrue(service.markFirstResponse(workOrder));
        assertNull(workOrder.getSlaStatus());
    }

    @Test
    void markFirstResponseById_savesOnlyWhenModified() {
        when(workOrderSlaRepository.findById(12L)).thenReturn(Optional.of(workOrder));

        service.markFirstResponse(12L);
        verify(workOrderSlaRepository).save(workOrder);

        service.markFirstResponse(12L);
        verify(workOrderSlaRepository).save(workOrder); // still one single save
    }

    // ------------------------------------------------------------ completion

    @Test
    void onWorkOrderCompleted_closesTheSla() {
        workOrder.setStatus(Status.COMPLETE);
        workOrder.setSlaResolutionDueAt(at(BASE + 240 * MINUTE));
        workOrder.setSlaStatus(SlaStatus.AT_RISK);
        workOrder.setCompletedOn(at(BASE + 200 * MINUTE));
        when(workOrderSlaRepository.findById(12L)).thenReturn(Optional.of(workOrder));

        service.onWorkOrderCompleted(new WorkOrderCompletedEvent(1L, 12L, "t", null, null));

        assertEquals(SlaStatus.MET, workOrder.getSlaStatus());
        verify(workOrderSlaRepository).save(workOrder);
    }

    @Test
    void onWorkOrderCompleted_ignoresWorkOrdersWithoutSla() {
        workOrder.setStatus(Status.COMPLETE);
        when(workOrderSlaRepository.findById(12L)).thenReturn(Optional.of(workOrder));

        service.onWorkOrderCompleted(new WorkOrderCompletedEvent(1L, 12L, "t", null, null));

        assertNull(workOrder.getSlaStatus());
        verify(workOrderSlaRepository, never()).save(any());
    }

    // ------------------------------------------------------------ scheduled refresh

    @Test
    void refreshOpenWorkOrders_updatesOnlyTheChangedStatusesGroupedByStatus() {
        now = at(BASE + 90 * MINUTE);
        Date created = at(BASE);
        Date due = at(BASE + 100 * MINUTE);
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1L, created, null, due, null, SlaStatus.ON_TRACK});  // 90% -> AT_RISK
        rows.add(new Object[]{2L, created, null, due, null, SlaStatus.AT_RISK});    // unchanged
        rows.add(new Object[]{3L, created, null, at(BASE + 60 * MINUTE), null, SlaStatus.AT_RISK}); // BREACHED
        rows.add(new Object[]{4L, created, null, at(BASE + 80 * MINUTE), null, null}); // BREACHED
        rows.add(new Object[]{5L, created, null, at(BASE + 1000 * MINUTE), null, SlaStatus.BREACHED}); // ON_TRACK
        when(workOrderSlaRepository.findOpenWithSla(Status.COMPLETE)).thenReturn(rows);

        service.refreshOpenWorkOrders();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(workOrderSlaRepository).updateSlaStatus(ids.capture(), eq(SlaStatus.AT_RISK));
        assertEquals(List.of(1L), List.copyOf(ids.getValue()));

        ArgumentCaptor<Collection<Long>> breached = ArgumentCaptor.forClass(Collection.class);
        verify(workOrderSlaRepository).updateSlaStatus(breached.capture(), eq(SlaStatus.BREACHED));
        assertEquals(List.of(3L, 4L), List.copyOf(breached.getValue()));

        ArgumentCaptor<Collection<Long>> onTrack = ArgumentCaptor.forClass(Collection.class);
        verify(workOrderSlaRepository).updateSlaStatus(onTrack.capture(), eq(SlaStatus.ON_TRACK));
        assertEquals(List.of(5L), List.copyOf(onTrack.getValue()));
    }

    @Test
    void refreshOpenWorkOrders_doesNothingWhenNoWorkOrderHasSla() {
        when(workOrderSlaRepository.findOpenWithSla(Status.COMPLETE)).thenReturn(Collections.emptyList());

        service.refreshOpenWorkOrders();

        verify(workOrderSlaRepository, never()).updateSlaStatus(any(), any());
    }
}
