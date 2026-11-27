package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.InvoiceStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"customer", "workOrder", "serviceContract", "lines"})
public class CustomerInvoice extends CompanyAudit {
    @NotNull
    @Column(nullable = false)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    private ServiceContract serviceContract;

    private Date issueDate;

    private Date dueDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(length = 10)
    private String currency = "USD";

    private double subtotal;

    private double taxRatePercent = 18.0;

    private double taxAmount;

    private double totalAmount;

    @Column(length = 2000)
    private String notes;

    private Date paidAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerInvoiceLine> lines = new ArrayList<>();
}
