package com.grash.controller;

import com.grash.dto.InvoicePatchDTO;
import com.grash.dto.InvoiceSearchDTO;
import com.grash.dto.InvoiceShowDTO;
import com.grash.dto.InvoiceSummaryDTO;
import com.grash.dto.SuccessResponse;
import com.grash.mapper.InvoiceMapper;
import com.grash.model.CustomerInvoice;
import com.grash.model.User;
import com.grash.service.CustomerInvoiceService;
import com.grash.service.InvoiceDocumentService;
import com.grash.service.UserService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Permissions (enforced in {@link CustomerInvoiceService}): read needs ANALYTICS in viewPermissions, write needs
 * SETTINGS in createPermissions.
 */
@RestController
@RequestMapping("/invoices")
@Tag(name = "Invoices", description = "Customer invoicing")
@RequiredArgsConstructor
public class InvoiceController {

    private final CustomerInvoiceService invoiceService;
    private final InvoiceDocumentService invoiceDocumentService;
    private final InvoiceMapper invoiceMapper;
    private final UserService userService;

    @PostMapping("/search")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Page<InvoiceShowDTO> search(@Parameter(description = "Invoice search criteria") @RequestBody InvoiceSearchDTO criteria,
                                       HttpServletRequest req) {
        User user = userService.whoami(req);
        return invoiceService.search(criteria, user).map(invoiceMapper::toShowDto);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public InvoiceSummaryDTO summary(@RequestParam(required = false) Long customerId,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date start,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date end,
                                     HttpServletRequest req) {
        User user = userService.whoami(req);
        return invoiceService.summary(customerId, start, end, user);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public InvoiceShowDTO getById(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        return invoiceMapper.toShowDto(invoiceService.getById(id, user));
    }

    @GetMapping(value = "/{id}/print", produces = MediaType.TEXT_HTML_VALUE)
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<String> print(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        CustomerInvoice invoice = invoiceService.getById(id, user);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_SECURITY_POLICY, "default-src 'none'; style-src 'unsafe-inline'")
                .body(invoiceDocumentService.renderHtml(invoice));
    }

    @PostMapping("/from-work-order/{workOrderId}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public InvoiceShowDTO createFromWorkOrder(@PathVariable("workOrderId") Long workOrderId, HttpServletRequest req) {
        User user = userService.whoami(req);
        return invoiceMapper.toShowDto(invoiceService.createFromWorkOrder(workOrderId, user));
    }

    @PostMapping("/from-contract/{contractId}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public InvoiceShowDTO createFromContract(@PathVariable("contractId") Long contractId, HttpServletRequest req) {
        User user = userService.whoami(req);
        return invoiceMapper.toShowDto(invoiceService.createFromContract(contractId, user));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public InvoiceShowDTO patch(@Parameter(description = "Invoice fields to update") @Valid @RequestBody InvoicePatchDTO patch,
                                @PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        return invoiceMapper.toShowDto(invoiceService.update(id, patch, user));
    }

    @PatchMapping("/{id}/issue")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public InvoiceShowDTO issue(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        return invoiceMapper.toShowDto(invoiceService.issue(id, user));
    }

    @PatchMapping("/{id}/mark-paid")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public InvoiceShowDTO markPaid(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        return invoiceMapper.toShowDto(invoiceService.markPaid(id, user));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public InvoiceShowDTO cancel(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        return invoiceMapper.toShowDto(invoiceService.cancel(id, user));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        invoiceService.delete(id, user);
        return ResponseEntity.ok(new SuccessResponse(true, "Deleted successfully"));
    }
}
