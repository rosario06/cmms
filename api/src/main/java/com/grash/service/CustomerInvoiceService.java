package com.grash.service;

import com.grash.dto.InvoiceLinePostDTO;
import com.grash.dto.InvoicePatchDTO;
import com.grash.dto.InvoiceSearchDTO;
import com.grash.dto.InvoiceSummaryDTO;
import com.grash.exception.CustomException;
import com.grash.model.AdditionalCost;
import com.grash.model.Company;
import com.grash.model.Customer;
import com.grash.model.CustomerInvoice;
import com.grash.model.CustomerInvoiceLine;
import com.grash.model.Labor;
import com.grash.model.PartQuantity;
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
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerInvoiceService {

    public static final double DEFAULT_TAX_RATE_PERCENT = 18.0;
    public static final int DEFAULT_DUE_DAYS = 30;
    public static final String DEFAULT_CURRENCY = "USD";
    public static final String COVERED_HOURS_DESCRIPTION = "Horas cubiertas por contrato";
    private static final int MAX_NUMBER_ATTEMPTS = 5;
    private static final Set<String> SORT_FIELDS = Set.of("id", "invoiceNumber", "issueDate", "dueDate", "status",
            "totalAmount", "createdAt");

    private final InvoiceRepository invoiceRepository;
    private final InvoiceContractRepository contractRepository;
    private final WorkOrderService workOrderService;
    private final LaborService laborService;
    private final PartQuantityService partQuantityService;
    private final AdditionalCostService additionalCostService;
    private final TransactionTemplate transactionTemplate;

    // ---------------------------------------------------------------- permissions

    public void checkCanRead(User user) {
        if (user == null || user.getRole() == null
                || !user.getRole().getViewPermissions().contains(PermissionEntity.ANALYTICS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }

    public void checkCanWrite(User user) {
        if (user == null || user.getRole() == null
                || !user.getRole().getCreatePermissions().contains(PermissionEntity.SETTINGS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }

    // ---------------------------------------------------------------- queries

    public CustomerInvoice getById(Long id, User user) {
        checkCanRead(user);
        return findOwned(id, user);
    }

    public Page<CustomerInvoice> search(InvoiceSearchDTO criteria, User user) {
        checkCanRead(user);
        String sortField = criteria.getSortField() != null && SORT_FIELDS.contains(criteria.getSortField())
                ? criteria.getSortField() : "id";
        Sort.Direction direction = criteria.getDirection() == null ? Sort.Direction.DESC : criteria.getDirection();
        int pageSize = Math.min(Math.max(criteria.getPageSize(), 1), 100);
        Pageable pageable = PageRequest.of(Math.max(criteria.getPageNum(), 0), pageSize, direction, sortField);
        return invoiceRepository.findAll(buildSpecification(user.getCompany().getId(), criteria.getStatus(),
                criteria.getCustomerId(), criteria.getStartDate(), criteria.getEndDate(), criteria.getText()),
                pageable);
    }

    public InvoiceSummaryDTO summary(Long customerId, Date start, Date end, User user) {
        checkCanRead(user);
        List<CustomerInvoice> invoices = invoiceRepository.findAll(buildSpecification(user.getCompany().getId(),
                null, customerId, start, end, null));
        double invoiced = 0, pending = 0, overdue = 0, paid = 0;
        long count = 0;
        for (CustomerInvoice invoice : invoices) {
            InvoiceStatus status = invoice.getStatus();
            if (status == null || status == InvoiceStatus.DRAFT || status == InvoiceStatus.CANCELLED) continue;
            double total = invoice.getTotalAmount();
            invoiced += total;
            count++;
            if (status == InvoiceStatus.ISSUED || status == InvoiceStatus.OVERDUE) pending += total;
            if (status == InvoiceStatus.OVERDUE) overdue += total;
            if (status == InvoiceStatus.PAID) paid += total;
        }
        return new InvoiceSummaryDTO(round2(invoiced), round2(pending), round2(overdue), round2(paid), count);
    }

    Specification<CustomerInvoice> buildSpecification(Long companyId, InvoiceStatus status, Long customerId,
                                                      Date start, Date end, String text) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("company").get("id"), companyId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (customerId != null) predicates.add(cb.equal(root.get("customer").get("id"), customerId));
            if (start != null) predicates.add(cb.greaterThanOrEqualTo(root.<Date>get("issueDate"), start));
            if (end != null) predicates.add(cb.lessThanOrEqualTo(root.<Date>get("issueDate"), end));
            if (text != null && !text.isBlank()) {
                String escaped = text.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%")
                        .replace("_", "\\_");
                predicates.add(cb.like(cb.lower(root.<String>get("invoiceNumber")), "%" + escaped + "%", '\\'));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ---------------------------------------------------------------- creation

    public CustomerInvoice createFromWorkOrder(Long workOrderId, User user) {
        checkCanWrite(user);
        Long companyId = user.getCompany().getId();
        Date now = new Date();
        return createWithNumber(companyId, now, number -> buildFromWorkOrder(workOrderId, companyId, number, now));
    }

    public CustomerInvoice createFromContract(Long contractId, User user) {
        checkCanWrite(user);
        Long companyId = user.getCompany().getId();
        Date now = new Date();
        return createWithNumber(companyId, now, number -> buildContractInvoice(contractId, companyId, number, now));
    }

    /**
     * Used by the scheduled job (no authenticated user). The candidate is only used to get ids: everything is
     * reloaded inside the transaction.
     */
    public CustomerInvoice generateScheduledContractInvoice(ServiceContract candidate, Date now) {
        Long companyId = candidate.getCompany().getId();
        Long contractId = candidate.getId();
        return createWithNumber(companyId, now, number -> buildContractInvoice(contractId, companyId, number, now));
    }

    /**
     * Runs the builder in a new transaction each attempt. If the unique index (company_id, invoice_number)
     * is violated by a concurrent request, the number is recomputed and the attempt repeated (bounded).
     */
    private CustomerInvoice createWithNumber(Long companyId, Date issueDate, Function<String, CustomerInvoice> builder) {
        for (int attempt = 1; attempt <= MAX_NUMBER_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> builder.apply(nextInvoiceNumber(companyId, issueDate)));
            } catch (DataIntegrityViolationException e) {
                log.warn("Invoice number collision for company {} (attempt {}/{})", companyId, attempt,
                        MAX_NUMBER_ATTEMPTS);
            }
        }
        throw new CustomException("Could not allocate a unique invoice number, please retry", HttpStatus.CONFLICT);
    }

    /**
     * Next number as INV-{year}-{4 digit sequence}, based on the highest existing number of that year/company.
     */
    public String nextInvoiceNumber(Long companyId, Date issueDate) {
        String prefix = "INV-" + yearOf(issueDate) + "-";
        List<String> latest = invoiceRepository.findLatestNumbers(companyId, prefix + "%", PageRequest.of(0, 1));
        long next = 1;
        if (latest != null && !latest.isEmpty() && latest.get(0) != null
                && latest.get(0).startsWith(prefix)) {
            try {
                next = Long.parseLong(latest.get(0).substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                log.warn("Unparsable invoice number {}", latest.get(0));
            }
        }
        return prefix + String.format("%04d", next);
    }

    private CustomerInvoice buildFromWorkOrder(Long workOrderId, Long companyId, String number, Date now) {
        WorkOrder workOrder = workOrderService.findById(workOrderId)
                .orElseThrow(() -> new CustomException("Work order not found", HttpStatus.NOT_FOUND));
        if (workOrder.getCompany() == null || !companyId.equals(workOrder.getCompany().getId()))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        if (workOrder.getStatus() != Status.COMPLETE)
            throw new CustomException("Only completed work orders can be invoiced", HttpStatus.BAD_REQUEST);
        if (invoiceRepository.existsByWorkOrder_IdAndStatusNot(workOrderId, InvoiceStatus.CANCELLED))
            throw new CustomException("This work order already has an invoice", HttpStatus.CONFLICT);

        ServiceContract contract = workOrder.getServiceContract();
        Customer customer = null;
        if (workOrder.getCustomers() != null && !workOrder.getCustomers().isEmpty())
            customer = workOrder.getCustomers().get(0);
        if (customer == null && contract != null) customer = contract.getCustomer();

        CustomerInvoice invoice = newInvoice(workOrder.getCompany(), number, customer, now);
        invoice.setWorkOrder(workOrder);
        invoice.setServiceContract(contract);
        if (customer == null) {
            invoice.setNotes("No customer is associated with the work order: assign one before issuing.");
        }

        addLaborLines(invoice, workOrder, contract);
        addPartLines(invoice, workOrderId, contract);
        addAdditionalCostLines(invoice, workOrderId);
        recalculate(invoice);
        return invoiceRepository.saveAndFlush(invoice);
    }

    private void addLaborLines(CustomerInvoice invoice, WorkOrder workOrder, ServiceContract contract) {
        double coverage = Math.max(0, workOrder.getContractCoveredHours());
        double coveredTotal = 0;
        for (Labor labor : laborService.findByWorkOrder(workOrder.getId())) {
            if (!labor.isIncludeToTotalTime() || labor.getDuration() <= 0) continue;
            double hours = labor.getDuration() / 3600.0;
            double covered = Math.min(coverage, hours);
            coverage -= covered;
            coveredTotal += covered;
            double billedHours = hours - covered;
            if (billedHours <= 1e-9) continue;
            String who = labor.getAssignedTo() != null ? labor.getAssignedTo().getFullName() : "";
            String description = "Labor" + (who.isBlank() ? "" : ": " + who)
                    + (covered > 0 ? " (" + round2(covered) + " h covered by contract)" : "");
            addLine(invoice, InvoiceItemType.LABOR, description, round2(billedHours),
                    resolveLaborRate(contract, labor));
        }
        // informative line (price 0) so that traceability is kept and a fully covered work order still has a line
        // (an invoice without lines cannot be issued)
        if (coveredTotal > 1e-9) {
            addLine(invoice, InvoiceItemType.LABOR, COVERED_HOURS_DESCRIPTION, round2(coveredTotal), 0);
        }
    }

    static double resolveLaborRate(ServiceContract contract, Labor labor) {
        if (contract != null && contract.getHourlyRateOverride() != null && contract.getHourlyRateOverride() > 0)
            return contract.getHourlyRateOverride();
        User user = labor.getAssignedTo();
        if (user != null && user.getBillableHourlyRate() > 0) return user.getBillableHourlyRate();
        return labor.getHourlyRate();
    }

    private void addPartLines(CustomerInvoice invoice, Long workOrderId, ServiceContract contract) {
        for (PartQuantity partQuantity : partQuantityService.findByWorkOrder(workOrderId)) {
            if (partQuantity.getQuantity() <= 0 || partQuantity.getPart() == null) continue;
            double price = partQuantity.getPart().getBillablePrice();
            if (price <= 0) {
                price = partQuantity.getPart().getCost();
                if (contract != null) price = price * (1 + contract.getPartsMarkupPercent() / 100.0);
            }
            addLine(invoice, InvoiceItemType.PART, "Part: " + partQuantity.getPart().getName(),
                    partQuantity.getQuantity(), round2(price));
        }
    }

    private void addAdditionalCostLines(CustomerInvoice invoice, Long workOrderId) {
        for (AdditionalCost cost : additionalCostService.findByWorkOrder(workOrderId)) {
            if (!cost.isIncludeToTotalCost()) continue;
            String description = cost.getDescription() == null || cost.getDescription().isBlank()
                    ? "Additional cost" : cost.getDescription();
            addLine(invoice, InvoiceItemType.ADDITIONAL_COST, description, 1, cost.getCost());
        }
    }

    private CustomerInvoice buildContractInvoice(Long contractId, Long companyId, String number, Date now) {
        ServiceContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new CustomException("Contract not found", HttpStatus.NOT_FOUND));
        if (contract.getCompany() == null || !companyId.equals(contract.getCompany().getId()))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        if (contract.getStatus() != ContractStatus.ACTIVE)
            throw new CustomException("Only active contracts can be invoiced", HttpStatus.BAD_REQUEST);
        if (isPeriodInvoiced(contract, now))
            throw new CustomException("The current billing period was already invoiced", HttpStatus.CONFLICT);

        CustomerInvoice invoice = newInvoice(contract.getCompany(), number, contract.getCustomer(), now);
        invoice.setServiceContract(contract);
        if (contract.getCustomer() == null)
            invoice.setNotes("No customer is associated with the contract: assign one before issuing.");
        BillingCycle cycle = contract.getBillingCycle() == null ? BillingCycle.MONTHLY : contract.getBillingCycle();
        int months = monthsOf(cycle);
        addLine(invoice, InvoiceItemType.CONTRACT_FEE,
                "Contract " + contract.getContractNumber() + " - " + contract.getName() + " (" + cycle + ", "
                        + months + (months == 1 ? " month)" : " months)"), months, contract.getMonthlyFee());
        recalculate(invoice);
        CustomerInvoice saved = invoiceRepository.saveAndFlush(invoice);
        contract.setLastInvoicedAt(now);
        contractRepository.save(contract);
        return saved;
    }

    // ---------------------------------------------------------------- contract periods

    public static int monthsOf(BillingCycle cycle) {
        return switch (cycle) {
            case QUARTERLY -> 3;
            case ANNUAL -> 12;
            case MONTHLY, ONE_TIME -> 1;
        };
    }

    /** Start of the calendar period (month, quarter, year) containing now; null for ONE_TIME. */
    public static Date periodStart(Date now, BillingCycle cycle) {
        if (cycle == BillingCycle.ONE_TIME) return null;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate d = now.toInstant().atZone(zone).toLocalDate();
        LocalDate start = switch (cycle) {
            case QUARTERLY -> d.withMonth(((d.getMonthValue() - 1) / 3) * 3 + 1).withDayOfMonth(1);
            case ANNUAL -> d.withDayOfYear(1);
            default -> d.withDayOfMonth(1);
        };
        return Date.from(start.atStartOfDay(zone).toInstant());
    }

    public static boolean isPeriodInvoiced(ServiceContract contract, Date now) {
        Date last = contract.getLastInvoicedAt();
        if (last == null) return false;
        BillingCycle cycle = contract.getBillingCycle() == null ? BillingCycle.MONTHLY : contract.getBillingCycle();
        Date start = periodStart(now, cycle);
        return start == null || !last.before(start);
    }

    /** Whether the scheduler must invoice this contract now. */
    public static boolean isDue(ServiceContract contract, Date now) {
        return contract.getStatus() == ContractStatus.ACTIVE
                && contract.isAutoInvoice()
                && (contract.getStartDate() == null || !contract.getStartDate().after(now))
                && (contract.getEndDate() == null || !contract.getEndDate().before(now))
                && !isPeriodInvoiced(contract, now);
    }

    // ---------------------------------------------------------------- edition and transitions

    @Transactional
    public CustomerInvoice update(Long id, InvoicePatchDTO patch, User user) {
        checkCanWrite(user);
        CustomerInvoice invoice = findOwned(id, user);
        requireStatus(invoice, "Only draft invoices can be edited", InvoiceStatus.DRAFT);
        if (patch.getNotes() != null) invoice.setNotes(patch.getNotes());
        if (patch.getDueDate() != null) invoice.setDueDate(patch.getDueDate());
        if (patch.getTaxRatePercent() != null) {
            if (patch.getTaxRatePercent() < 0 || patch.getTaxRatePercent() > 100)
                throw new CustomException("Tax rate must be between 0 and 100", HttpStatus.BAD_REQUEST);
            invoice.setTaxRatePercent(patch.getTaxRatePercent());
        }
        if (patch.getCurrency() != null) {
            String currency = patch.getCurrency().trim().toUpperCase();
            if (currency.isEmpty() || currency.length() > 10)
                throw new CustomException("Invalid currency", HttpStatus.BAD_REQUEST);
            invoice.setCurrency(currency);
        }
        if (patch.getLines() != null) {
            invoice.getLines().clear();
            for (InvoiceLinePostDTO dto : patch.getLines()) {
                if (dto.getItemType() == null || dto.getQuantity() < 0)
                    throw new CustomException("Invalid invoice line", HttpStatus.BAD_REQUEST);
                addLine(invoice, dto.getItemType(), dto.getDescription() == null ? "" : dto.getDescription(),
                        dto.getQuantity(), dto.getUnitPrice());
            }
        }
        recalculate(invoice);
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public CustomerInvoice issue(Long id, User user) {
        checkCanWrite(user);
        CustomerInvoice invoice = findOwned(id, user);
        requireStatus(invoice, "Only draft invoices can be issued", InvoiceStatus.DRAFT);
        if (invoice.getLines() == null || invoice.getLines().isEmpty())
            throw new CustomException("An invoice without lines cannot be issued", HttpStatus.BAD_REQUEST);
        Date now = new Date();
        long termMillis = DEFAULT_DUE_DAYS * 86_400_000L;
        if (invoice.getIssueDate() != null && invoice.getDueDate() != null
                && invoice.getDueDate().after(invoice.getIssueDate())) {
            // keep the payment term chosen at creation/edition
            termMillis = invoice.getDueDate().getTime() - invoice.getIssueDate().getTime();
        }
        invoice.setIssueDate(now);
        invoice.setDueDate(new Date(now.getTime() + termMillis));
        invoice.setStatus(InvoiceStatus.ISSUED);
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public CustomerInvoice markPaid(Long id, User user) {
        checkCanWrite(user);
        CustomerInvoice invoice = findOwned(id, user);
        requireStatus(invoice, "Only issued or overdue invoices can be marked as paid", InvoiceStatus.ISSUED,
                InvoiceStatus.OVERDUE);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(new Date());
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public CustomerInvoice cancel(Long id, User user) {
        checkCanWrite(user);
        CustomerInvoice invoice = findOwned(id, user);
        requireStatus(invoice, "Paid or already cancelled invoices cannot be cancelled", InvoiceStatus.DRAFT,
                InvoiceStatus.ISSUED, InvoiceStatus.OVERDUE);
        invoice.setStatus(InvoiceStatus.CANCELLED);
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public void delete(Long id, User user) {
        checkCanWrite(user);
        CustomerInvoice invoice = findOwned(id, user);
        requireStatus(invoice, "Only draft invoices can be deleted", InvoiceStatus.DRAFT);
        invoiceRepository.delete(invoice);
    }

    /** ISSUED invoices whose due date has passed become OVERDUE. Returns how many were updated. */
    @Transactional
    public int markOverdueInvoices(Date now) {
        List<CustomerInvoice> overdue = invoiceRepository.findByStatusAndDueDateBefore(InvoiceStatus.ISSUED, now);
        overdue.forEach(invoice -> invoice.setStatus(InvoiceStatus.OVERDUE));
        invoiceRepository.saveAll(overdue);
        return overdue.size();
    }

    // ---------------------------------------------------------------- helpers

    private CustomerInvoice findOwned(Long id, User user) {
        CustomerInvoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new CustomException("Invoice not found", HttpStatus.NOT_FOUND));
        if (invoice.getCompany() == null || user.getCompany() == null
                || !invoice.getCompany().getId().equals(user.getCompany().getId()))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        return invoice;
    }

    private void requireStatus(CustomerInvoice invoice, String message, InvoiceStatus... allowed) {
        for (InvoiceStatus status : allowed) {
            if (invoice.getStatus() == status) return;
        }
        throw new CustomException(message, HttpStatus.BAD_REQUEST);
    }

    private CustomerInvoice newInvoice(Company company, String number, Customer customer, Date now) {
        CustomerInvoice invoice = new CustomerInvoice();
        invoice.setCompany(company);
        invoice.setInvoiceNumber(number);
        invoice.setCustomer(customer);
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setIssueDate(now);
        invoice.setDueDate(new Date(now.getTime() + DEFAULT_DUE_DAYS * 86_400_000L));
        invoice.setTaxRatePercent(DEFAULT_TAX_RATE_PERCENT);
        String currency = DEFAULT_CURRENCY;
        if (customer != null && customer.getBillingCurrency() != null
                && customer.getBillingCurrency().getCode() != null
                && !customer.getBillingCurrency().getCode().isBlank()) {
            currency = customer.getBillingCurrency().getCode();
        }
        invoice.setCurrency(currency);
        return invoice;
    }

    static void addLine(CustomerInvoice invoice, InvoiceItemType type, String description, double quantity,
                        double unitPrice) {
        CustomerInvoiceLine line = new CustomerInvoiceLine();
        line.setInvoice(invoice);
        line.setCompany(invoice.getCompany());
        line.setItemType(type);
        line.setDescription(description);
        line.setQuantity(quantity);
        line.setUnitPrice(unitPrice);
        line.setLineTotal(round2(quantity * unitPrice));
        invoice.getLines().add(line);
    }

    /** subtotal = sum of lines, tax = subtotal x rate / 100, total = subtotal + tax (all rounded to 2 decimals). */
    public static void recalculate(CustomerInvoice invoice) {
        double subtotal = 0;
        for (CustomerInvoiceLine line : invoice.getLines()) {
            line.setLineTotal(round2(line.getQuantity() * line.getUnitPrice()));
            subtotal += line.getLineTotal();
        }
        subtotal = round2(subtotal);
        double tax = round2(subtotal * invoice.getTaxRatePercent() / 100.0);
        invoice.setSubtotal(subtotal);
        invoice.setTaxAmount(tax);
        invoice.setTotalAmount(round2(subtotal + tax));
    }

    public static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static int yearOf(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).getYear();
    }
}
