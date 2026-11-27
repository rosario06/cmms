package com.grash.service;

import com.grash.dto.InvoiceLinePostDTO;
import com.grash.dto.InvoicePatchDTO;
import com.grash.dto.InvoiceSearchDTO;
import com.grash.exception.CustomException;
import com.grash.model.AdditionalCost;
import com.grash.model.Company;
import com.grash.model.Currency;
import com.grash.model.Customer;
import com.grash.model.CustomerInvoice;
import com.grash.model.CustomerInvoiceLine;
import com.grash.model.Labor;
import com.grash.model.Part;
import com.grash.model.PartQuantity;
import com.grash.model.Role;
import com.grash.model.ServiceContract;
import com.grash.model.User;
import com.grash.model.WorkOrder;
import com.grash.model.enums.BillingCycle;
import com.grash.model.enums.ContractStatus;
import com.grash.model.enums.InvoiceItemType;
import com.grash.model.enums.InvoiceStatus;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.Status;
import com.grash.repository.InvoiceContractRepository;
import com.grash.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerInvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoiceContractRepository contractRepository;
    @Mock
    private WorkOrderService workOrderService;
    @Mock
    private LaborService laborService;
    @Mock
    private PartQuantityService partQuantityService;
    @Mock
    private AdditionalCostService additionalCostService;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private CustomerInvoiceService service;

    private Company company;
    private User writer;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv ->
                ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        lenient().when(invoiceRepository.saveAndFlush(any(CustomerInvoice.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(invoiceRepository.save(any(CustomerInvoice.class))).thenAnswer(inv -> inv.getArgument(0));
        company = company(1L);
        writer = user(company, Set.of(), Set.of(PermissionEntity.SETTINGS));
    }

    // ------------------------------------------------------------------ helpers

    private static Company company(Long id) {
        Company c = new Company();
        c.setId(id);
        c.setName("Acme");
        return c;
    }

    private static User user(Company company, Set<PermissionEntity> view, Set<PermissionEntity> create) {
        Role role = new Role();
        role.setViewPermissions(new HashSet<>(view));
        role.setCreatePermissions(new HashSet<>(create));
        User user = new User();
        user.setId(99L);
        user.setCompany(company);
        user.setRole(role);
        return user;
    }

    private static Date date(int y, int m, int d) {
        return Date.from(LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private WorkOrder completeWorkOrder(Long id) {
        WorkOrder wo = new WorkOrder();
        wo.setId(id);
        wo.setCompany(company);
        wo.setStatus(Status.COMPLETE);
        lenient().when(workOrderService.findById(id)).thenReturn(Optional.of(wo));
        return wo;
    }

    private static Labor labor(User user, long seconds, long rate) {
        Labor labor = new Labor();
        labor.setAssignedTo(user);
        labor.setDuration(seconds);
        labor.setHourlyRate(rate);
        return labor;
    }

    private static User technician(double billableRate) {
        User tech = new User();
        tech.setFirstName("Ana");
        tech.setLastName("Perez");
        tech.setBillableHourlyRate(billableRate);
        return tech;
    }

    private ServiceContract contract(BillingCycle cycle, double fee) {
        ServiceContract c = new ServiceContract();
        c.setId(5L);
        c.setCompany(company);
        c.setContractNumber("CT-1");
        c.setName("Maintenance");
        c.setStatus(ContractStatus.ACTIVE);
        c.setBillingCycle(cycle);
        c.setMonthlyFee(fee);
        lenient().when(contractRepository.findById(5L)).thenReturn(Optional.of(c));
        return c;
    }

    private CustomerInvoice invoiceWithStatus(InvoiceStatus status) {
        CustomerInvoice invoice = new CustomerInvoice();
        invoice.setId(7L);
        invoice.setCompany(company);
        invoice.setStatus(status);
        invoice.setIssueDate(date(2026, 1, 1));
        invoice.setDueDate(date(2026, 1, 31));
        when(invoiceRepository.findById(7L)).thenReturn(Optional.of(invoice));
        return invoice;
    }

    private static CustomerInvoiceLine line(double qty, double price) {
        CustomerInvoiceLine line = new CustomerInvoiceLine();
        line.setItemType(InvoiceItemType.PART);
        line.setQuantity(qty);
        line.setUnitPrice(price);
        return line;
    }

    private static CustomerInvoiceLine firstLine(CustomerInvoice invoice, InvoiceItemType type) {
        return invoice.getLines().stream().filter(l -> l.getItemType() == type).findFirst().orElseThrow();
    }

    private static void assertStatus(HttpStatus expected, Runnable action) {
        CustomException e = assertThrows(CustomException.class, action::run);
        assertEquals(expected, e.getHttpStatus());
    }

    // ------------------------------------------------------------------ numbering

    @Test
    void nextInvoiceNumber_firstOfYear() {
        assertEquals("INV-2026-0001", service.nextInvoiceNumber(1L, date(2026, 3, 1)));
    }

    @Test
    void nextInvoiceNumber_incrementsHighestAndPads() {
        when(invoiceRepository.findLatestNumbers(eq(1L), eq("INV-2026-%"), any(Pageable.class)))
                .thenReturn(List.of("INV-2026-0009"));
        assertEquals("INV-2026-0010", service.nextInvoiceNumber(1L, date(2026, 12, 31)));
    }

    @Test
    void nextInvoiceNumber_growsBeyondFourDigits() {
        when(invoiceRepository.findLatestNumbers(eq(1L), eq("INV-2026-%"), any(Pageable.class)))
                .thenReturn(List.of("INV-2026-9999"));
        assertEquals("INV-2026-10000", service.nextInvoiceNumber(1L, date(2026, 5, 5)));
    }

    @Test
    void nextInvoiceNumber_restartsEachYear() {
        when(invoiceRepository.findLatestNumbers(eq(1L), eq("INV-2027-%"), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        assertEquals("INV-2027-0001", service.nextInvoiceNumber(1L, date(2027, 1, 2)));
    }

    @Test
    void createFromContract_retriesOnNumberCollision() {
        contract(BillingCycle.MONTHLY, 100);
        String prefix = "INV-" + LocalDate.now().getYear() + "-";
        when(invoiceRepository.findLatestNumbers(eq(1L), anyString(), any(Pageable.class)))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(prefix + "0001"));
        when(invoiceRepository.saveAndFlush(any(CustomerInvoice.class)))
                .thenThrow(new DataIntegrityViolationException("uq_customer_invoice_number"))
                .thenAnswer(inv -> inv.getArgument(0));

        CustomerInvoice invoice = service.createFromContract(5L, writer);

        assertEquals(prefix + "0002", invoice.getInvoiceNumber());
    }

    @Test
    void createFromContract_givesUpAfterBoundedRetries() {
        contract(BillingCycle.MONTHLY, 100);
        when(invoiceRepository.saveAndFlush(any(CustomerInvoice.class)))
                .thenThrow(new DataIntegrityViolationException("uq_customer_invoice_number"));

        assertStatus(HttpStatus.CONFLICT, () -> service.createFromContract(5L, writer));
    }

    // ------------------------------------------------------------------ totals

    @Test
    void recalculate_roundsToTwoDecimals() {
        CustomerInvoice invoice = new CustomerInvoice();
        invoice.setTaxRatePercent(18.0);
        invoice.getLines().add(line(1, 10.10));
        invoice.getLines().add(line(1, 20.20));

        CustomerInvoiceService.recalculate(invoice);

        assertEquals(30.3, invoice.getSubtotal(), 0.0001);
        assertEquals(5.45, invoice.getTaxAmount(), 0.0001);
        assertEquals(35.75, invoice.getTotalAmount(), 0.0001);
    }

    @Test
    void round2_roundsHalfUp() {
        assertEquals(1.01, CustomerInvoiceService.round2(1.005), 0.0);
        assertEquals(2.35, CustomerInvoiceService.round2(2.345), 0.0);
        assertEquals(0.0, CustomerInvoiceService.round2(0.0), 0.0);
    }

    // ------------------------------------------------------------------ from work order

    @Test
    void createFromWorkOrder_rejectsIncompleteOrder() {
        WorkOrder wo = completeWorkOrder(10L);
        wo.setStatus(Status.IN_PROGRESS);

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createFromWorkOrder(10L, writer));
        verify(invoiceRepository, never()).saveAndFlush(any(CustomerInvoice.class));
    }

    @Test
    void createFromWorkOrder_rejectsDuplicateWithConflict() {
        completeWorkOrder(10L);
        when(invoiceRepository.existsByWorkOrder_IdAndStatusNot(10L, InvoiceStatus.CANCELLED)).thenReturn(true);

        assertStatus(HttpStatus.CONFLICT, () -> service.createFromWorkOrder(10L, writer));
    }

    @Test
    void createFromWorkOrder_workOrderOfAnotherCompanyIsForbidden() {
        WorkOrder wo = completeWorkOrder(10L);
        wo.setCompany(company(2L));

        assertStatus(HttpStatus.FORBIDDEN, () -> service.createFromWorkOrder(10L, writer));
    }

    @Test
    void createFromWorkOrder_unknownOrderIs404() {
        when(workOrderService.findById(10L)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.createFromWorkOrder(10L, writer));
    }

    @Test
    void createFromWorkOrder_discountsContractCoveredHours() {
        WorkOrder wo = completeWorkOrder(10L);
        wo.setContractCoveredHours(3);
        // A: 2h at billable rate 50 (fully covered). B: 4h, no billable rate -> labor rate 40, 1h covered.
        when(laborService.findByWorkOrder(10L)).thenReturn(List.of(
                labor(technician(50), 7200, 20), labor(technician(0), 14400, 40)));

        CustomerInvoice invoice = service.createFromWorkOrder(10L, writer);

        assertEquals(1, invoice.getLines().size());
        CustomerInvoiceLine labor = firstLine(invoice, InvoiceItemType.LABOR);
        assertEquals(3.0, labor.getQuantity(), 0.0001);
        assertEquals(40.0, labor.getUnitPrice(), 0.0001);
        assertEquals(120.0, labor.getLineTotal(), 0.0001);
        assertEquals(120.0, invoice.getSubtotal(), 0.0001);
        assertEquals(21.6, invoice.getTaxAmount(), 0.0001);
        assertEquals(141.6, invoice.getTotalAmount(), 0.0001);
        assertEquals(InvoiceStatus.DRAFT, invoice.getStatus());
        assertEquals(wo, invoice.getWorkOrder());
        assertEquals("USD", invoice.getCurrency());
        assertEquals(18.0, invoice.getTaxRatePercent(), 0.0);
    }

    @Test
    void createFromWorkOrder_noLaborLineWhenEverythingIsCovered() {
        WorkOrder wo = completeWorkOrder(10L);
        wo.setContractCoveredHours(10);
        when(laborService.findByWorkOrder(10L)).thenReturn(List.of(labor(technician(50), 7200, 20)));

        CustomerInvoice invoice = service.createFromWorkOrder(10L, writer);

        assertTrue(invoice.getLines().isEmpty());
        assertEquals(0.0, invoice.getTotalAmount(), 0.0);
    }

    @Test
    void createFromWorkOrder_contractHourlyRateOverrideWins() {
        WorkOrder wo = completeWorkOrder(10L);
        ServiceContract contract = new ServiceContract();
        contract.setHourlyRateOverride(100.0);
        wo.setServiceContract(contract);
        when(laborService.findByWorkOrder(10L)).thenReturn(List.of(labor(technician(50), 3600, 20)));

        CustomerInvoice invoice = service.createFromWorkOrder(10L, writer);

        assertEquals(100.0, firstLine(invoice, InvoiceItemType.LABOR).getUnitPrice(), 0.0);
        assertEquals(contract, invoice.getServiceContract());
    }

    @Test
    void createFromWorkOrder_usesTechnicianBillableRateBeforeLaborRate() {
        completeWorkOrder(10L);
        when(laborService.findByWorkOrder(10L)).thenReturn(List.of(labor(technician(55), 3600, 20)));

        CustomerInvoice invoice = service.createFromWorkOrder(10L, writer);

        assertEquals(55.0, firstLine(invoice, InvoiceItemType.LABOR).getUnitPrice(), 0.0);
    }

    @Test
    void createFromWorkOrder_ignoresLaborExcludedFromTotalTime() {
        completeWorkOrder(10L);
        Labor excluded = labor(technician(55), 3600, 20);
        excluded.setIncludeToTotalTime(false);
        when(laborService.findByWorkOrder(10L)).thenReturn(List.of(excluded));

        assertTrue(service.createFromWorkOrder(10L, writer).getLines().isEmpty());
    }

    @Test
    void createFromWorkOrder_partsUseBillablePriceOrMarkup() {
        WorkOrder wo = completeWorkOrder(10L);
        ServiceContract contract = new ServiceContract();
        contract.setPartsMarkupPercent(20);
        wo.setServiceContract(contract);
        Part billable = new Part();
        billable.setName("Bearing");
        billable.setCost(10);
        billable.setBillablePrice(15);
        Part marked = new Part();
        marked.setName("Belt");
        marked.setCost(10);
        when(partQuantityService.findByWorkOrder(10L)).thenReturn(List.of(
                new PartQuantity(billable, wo, null, 2), new PartQuantity(marked, wo, null, 3)));

        CustomerInvoice invoice = service.createFromWorkOrder(10L, writer);

        List<CustomerInvoiceLine> parts = invoice.getLines();
        assertEquals(2, parts.size());
        assertEquals(15.0, parts.get(0).getUnitPrice(), 0.0);
        assertEquals(30.0, parts.get(0).getLineTotal(), 0.0);
        assertEquals(12.0, parts.get(1).getUnitPrice(), 0.0);
        assertEquals(36.0, parts.get(1).getLineTotal(), 0.0);
        assertEquals(66.0, invoice.getSubtotal(), 0.0);
    }

    @Test
    void createFromWorkOrder_partWithoutContractNorBillablePriceUsesCost() {
        WorkOrder wo = completeWorkOrder(10L);
        Part part = new Part();
        part.setName("Filter");
        part.setCost(8);
        when(partQuantityService.findByWorkOrder(10L)).thenReturn(List.of(new PartQuantity(part, wo, null, 1)));

        assertEquals(8.0, firstLine(service.createFromWorkOrder(10L, writer), InvoiceItemType.PART).getUnitPrice(),
                0.0);
    }

    @Test
    void createFromWorkOrder_onlyIncludedAdditionalCosts() {
        completeWorkOrder(10L);
        AdditionalCost included = new AdditionalCost();
        included.setCost(25);
        included.setDescription("Transport");
        included.setIncludeToTotalCost(true);
        AdditionalCost excluded = new AdditionalCost();
        excluded.setCost(99);
        excluded.setIncludeToTotalCost(false);
        when(additionalCostService.findByWorkOrder(10L)).thenReturn(List.of(included, excluded));

        CustomerInvoice invoice = service.createFromWorkOrder(10L, writer);

        assertEquals(1, invoice.getLines().size());
        assertEquals("Transport", invoice.getLines().get(0).getDescription());
        assertEquals(25.0, invoice.getSubtotal(), 0.0);
    }

    @Test
    void createFromWorkOrder_usesFirstCustomerAndItsCurrency() {
        WorkOrder wo = completeWorkOrder(10L);
        Customer customer = new Customer();
        customer.setName("Client");
        Currency currency = new Currency();
        currency.setCode("DOP");
        customer.setBillingCurrency(currency);
        wo.getCustomers().add(customer);

        CustomerInvoice invoice = service.createFromWorkOrder(10L, writer);

        assertEquals(customer, invoice.getCustomer());
        assertEquals("DOP", invoice.getCurrency());
        assertNull(invoice.getNotes());
    }

    @Test
    void createFromWorkOrder_withoutCustomerIsAllowedButNoted() {
        completeWorkOrder(10L);

        CustomerInvoice invoice = service.createFromWorkOrder(10L, writer);

        assertNull(invoice.getCustomer());
        assertNotNull(invoice.getNotes());
    }

    @Test
    void createFromWorkOrder_fallsBackToContractCustomer() {
        WorkOrder wo = completeWorkOrder(10L);
        ServiceContract contract = new ServiceContract();
        Customer customer = new Customer();
        customer.setName("Contract client");
        contract.setCustomer(customer);
        wo.setServiceContract(contract);

        assertEquals(customer, service.createFromWorkOrder(10L, writer).getCustomer());
    }

    // ------------------------------------------------------------------ from contract

    @Test
    void createFromContract_feePerCycle() {
        assertEquals(100.0, fee(BillingCycle.MONTHLY), 0.0);
        assertEquals(300.0, fee(BillingCycle.QUARTERLY), 0.0);
        assertEquals(1200.0, fee(BillingCycle.ANNUAL), 0.0);
        assertEquals(100.0, fee(BillingCycle.ONE_TIME), 0.0);
    }

    private double fee(BillingCycle cycle) {
        contract(cycle, 100);
        CustomerInvoice invoice = service.createFromContract(5L, writer);
        assertEquals(InvoiceStatus.DRAFT, invoice.getStatus());
        assertEquals(InvoiceItemType.CONTRACT_FEE, invoice.getLines().get(0).getItemType());
        return invoice.getSubtotal();
    }

    @Test
    void createFromContract_updatesLastInvoicedAtAndTotals() {
        ServiceContract contract = contract(BillingCycle.QUARTERLY, 100);

        CustomerInvoice invoice = service.createFromContract(5L, writer);

        assertNotNull(contract.getLastInvoicedAt());
        verify(contractRepository).save(contract);
        assertEquals(54.0, invoice.getTaxAmount(), 0.0001);
        assertEquals(354.0, invoice.getTotalAmount(), 0.0001);
        assertEquals(contract, invoice.getServiceContract());
    }

    @Test
    void createFromContract_duplicateInCurrentPeriodIs409() {
        ServiceContract contract = contract(BillingCycle.MONTHLY, 100);
        contract.setLastInvoicedAt(new Date());

        assertStatus(HttpStatus.CONFLICT, () -> service.createFromContract(5L, writer));
        verify(invoiceRepository, never()).saveAndFlush(any(CustomerInvoice.class));
    }

    @Test
    void createFromContract_oneTimeOnlyOnce() {
        ServiceContract contract = contract(BillingCycle.ONE_TIME, 100);
        contract.setLastInvoicedAt(date(2020, 1, 1));

        assertStatus(HttpStatus.CONFLICT, () -> service.createFromContract(5L, writer));
    }

    @Test
    void createFromContract_previousPeriodDoesNotBlock() {
        ServiceContract contract = contract(BillingCycle.MONTHLY, 100);
        contract.setLastInvoicedAt(date(2020, 1, 15));

        assertEquals(100.0, service.createFromContract(5L, writer).getSubtotal(), 0.0);
    }

    @Test
    void createFromContract_requiresActiveContract() {
        ServiceContract contract = contract(BillingCycle.MONTHLY, 100);
        contract.setStatus(ContractStatus.DRAFT);

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createFromContract(5L, writer));
    }

    @Test
    void isDue_and_periodStart() {
        ServiceContract contract = new ServiceContract();
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setAutoInvoice(true);
        contract.setBillingCycle(BillingCycle.QUARTERLY);
        Date now = date(2026, 5, 20);

        assertTrue(CustomerInvoiceService.isDue(contract, now));
        assertEquals(date(2026, 4, 1), CustomerInvoiceService.periodStart(now, BillingCycle.QUARTERLY));
        assertEquals(date(2026, 5, 1), CustomerInvoiceService.periodStart(now, BillingCycle.MONTHLY));
        assertEquals(date(2026, 1, 1), CustomerInvoiceService.periodStart(now, BillingCycle.ANNUAL));
        assertNull(CustomerInvoiceService.periodStart(now, BillingCycle.ONE_TIME));

        contract.setLastInvoicedAt(date(2026, 4, 2));
        assertFalse(CustomerInvoiceService.isDue(contract, now));
        contract.setLastInvoicedAt(date(2026, 3, 31));
        assertTrue(CustomerInvoiceService.isDue(contract, now));

        contract.setAutoInvoice(false);
        assertFalse(CustomerInvoiceService.isDue(contract, now));
    }

    // ------------------------------------------------------------------ transitions

    @Test
    void issue_movesDraftToIssuedAndKeepsPaymentTerm() {
        CustomerInvoice invoice = invoiceWithStatus(InvoiceStatus.DRAFT);
        invoice.getLines().add(line(1, 10));

        CustomerInvoice issued = service.issue(7L, writer);

        assertEquals(InvoiceStatus.ISSUED, issued.getStatus());
        long term = issued.getDueDate().getTime() - issued.getIssueDate().getTime();
        assertEquals(30L * 86_400_000L, term, 3_700_000L); // 30 days, tolerant to DST shifts
    }

    @Test
    void issue_requiresDraftAndLines() {
        invoiceWithStatus(InvoiceStatus.PAID);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.issue(7L, writer));
    }

    @Test
    void issue_withoutLinesIsRejected() {
        invoiceWithStatus(InvoiceStatus.DRAFT);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.issue(7L, writer));
    }

    @Test
    void markPaid_fromIssuedAndOverdue() {
        CustomerInvoice invoice = invoiceWithStatus(InvoiceStatus.ISSUED);
        assertEquals(InvoiceStatus.PAID, service.markPaid(7L, writer).getStatus());
        assertNotNull(invoice.getPaidAt());

        invoice.setStatus(InvoiceStatus.OVERDUE);
        invoice.setPaidAt(null);
        assertEquals(InvoiceStatus.PAID, service.markPaid(7L, writer).getStatus());
    }

    @Test
    void markPaid_otherStatesAre400() {
        CustomerInvoice invoice = invoiceWithStatus(InvoiceStatus.DRAFT);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.markPaid(7L, writer));
        invoice.setStatus(InvoiceStatus.CANCELLED);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.markPaid(7L, writer));
        invoice.setStatus(InvoiceStatus.PAID);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.markPaid(7L, writer));
    }

    @Test
    void cancel_rules() {
        CustomerInvoice invoice = invoiceWithStatus(InvoiceStatus.ISSUED);
        assertEquals(InvoiceStatus.CANCELLED, service.cancel(7L, writer).getStatus());
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.cancel(7L, writer));
        invoice.setStatus(InvoiceStatus.PAID);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.cancel(7L, writer));
    }

    @Test
    void delete_onlyDraft() {
        CustomerInvoice invoice = invoiceWithStatus(InvoiceStatus.ISSUED);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.delete(7L, writer));
        verify(invoiceRepository, never()).delete(any(CustomerInvoice.class));

        invoice.setStatus(InvoiceStatus.DRAFT);
        service.delete(7L, writer);
        verify(invoiceRepository).delete(invoice);
    }

    @Test
    void update_onlyDraft() {
        invoiceWithStatus(InvoiceStatus.ISSUED);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.update(7L, new InvoicePatchDTO(), writer));
    }

    @Test
    void update_replacesLinesAndRecalculates() {
        CustomerInvoice invoice = invoiceWithStatus(InvoiceStatus.DRAFT);
        invoice.setCompany(company);
        invoice.getLines().add(line(1, 999));
        InvoiceLinePostDTO dto = new InvoiceLinePostDTO();
        dto.setItemType(InvoiceItemType.ADDITIONAL_COST);
        dto.setDescription("Extra");
        dto.setQuantity(2);
        dto.setUnitPrice(50);
        InvoicePatchDTO patch = new InvoicePatchDTO();
        patch.setLines(new ArrayList<>(List.of(dto)));
        patch.setTaxRatePercent(10.0);
        patch.setCurrency(" dop ");
        patch.setNotes("hello");

        CustomerInvoice updated = service.update(7L, patch, writer);

        assertEquals(1, updated.getLines().size());
        assertEquals(100.0, updated.getSubtotal(), 0.0);
        assertEquals(10.0, updated.getTaxAmount(), 0.0);
        assertEquals(110.0, updated.getTotalAmount(), 0.0);
        assertEquals("DOP", updated.getCurrency());
        assertEquals("hello", updated.getNotes());
    }

    @Test
    void update_rejectsInvalidValues() {
        invoiceWithStatus(InvoiceStatus.DRAFT);
        InvoicePatchDTO badTax = new InvoicePatchDTO();
        badTax.setTaxRatePercent(150.0);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.update(7L, badTax, writer));

        InvoiceLinePostDTO negative = new InvoiceLinePostDTO();
        negative.setItemType(InvoiceItemType.PART);
        negative.setQuantity(-1);
        InvoicePatchDTO badLine = new InvoicePatchDTO();
        badLine.setLines(List.of(negative));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.update(7L, badLine, writer));
    }

    @Test
    void markOverdueInvoices_flipsIssuedPastDue() {
        CustomerInvoice a = new CustomerInvoice();
        a.setStatus(InvoiceStatus.ISSUED);
        CustomerInvoice b = new CustomerInvoice();
        b.setStatus(InvoiceStatus.ISSUED);
        Date now = new Date();
        when(invoiceRepository.findByStatusAndDueDateBefore(InvoiceStatus.ISSUED, now)).thenReturn(List.of(a, b));

        assertEquals(2, service.markOverdueInvoices(now));
        assertEquals(InvoiceStatus.OVERDUE, a.getStatus());
        assertEquals(InvoiceStatus.OVERDUE, b.getStatus());
        verify(invoiceRepository).saveAll(List.of(a, b));
    }

    // ------------------------------------------------------------------ permissions and tenancy

    @Test
    void writeOperationsRequireSettingsCreatePermission() {
        User reader = user(company, Set.of(PermissionEntity.ANALYTICS), Set.of());

        assertStatus(HttpStatus.FORBIDDEN, () -> service.createFromWorkOrder(10L, reader));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.createFromContract(5L, reader));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.update(7L, new InvoicePatchDTO(), reader));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.issue(7L, reader));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.markPaid(7L, reader));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.cancel(7L, reader));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.delete(7L, reader));
    }

    @Test
    void readOperationsRequireAnalyticsViewPermission() {
        assertStatus(HttpStatus.FORBIDDEN, () -> service.getById(7L, writer));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.search(new InvoiceSearchDTO(), writer));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.summary(null, null, null, writer));
    }

    @Test
    void getById_otherCompanyIsForbidden() {
        User reader = user(company(2L), Set.of(PermissionEntity.ANALYTICS), Set.of());
        invoiceWithStatus(InvoiceStatus.DRAFT);

        assertStatus(HttpStatus.FORBIDDEN, () -> service.getById(7L, reader));
    }

    @Test
    void getById_unknownIs404() {
        User reader = user(company, Set.of(PermissionEntity.ANALYTICS), Set.of());
        when(invoiceRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND, () -> service.getById(7L, reader));
    }

    @Test
    void summary_aggregatesByStatus() {
        User reader = user(company, Set.of(PermissionEntity.ANALYTICS), Set.of());
        when(invoiceRepository.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<CustomerInvoice>>any()))
                .thenReturn(List.of(
                        total(InvoiceStatus.ISSUED, 100.10), total(InvoiceStatus.PAID, 200),
                        total(InvoiceStatus.OVERDUE, 50), total(InvoiceStatus.DRAFT, 999),
                        total(InvoiceStatus.CANCELLED, 888)));

        var summary = service.summary(null, null, null, reader);

        assertEquals(350.10, summary.getTotalInvoiced(), 0.0001);
        assertEquals(150.10, summary.getPendingCollection(), 0.0001);
        assertEquals(50.0, summary.getOverdue(), 0.0001);
        assertEquals(200.0, summary.getPaid(), 0.0001);
        assertEquals(3, summary.getCount());
    }

    private static CustomerInvoice total(InvoiceStatus status, double total) {
        CustomerInvoice invoice = new CustomerInvoice();
        invoice.setStatus(status);
        invoice.setTotalAmount(total);
        return invoice;
    }
}
