package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.BillingCycle;
import com.grash.model.enums.ContractStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Date;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"customer"})
public class ServiceContract extends CompanyAudit {
    @NotNull
    @Column(nullable = false)
    private String contractNumber;

    @NotNull
    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractStatus status = ContractStatus.DRAFT;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillingCycle billingCycle = BillingCycle.MONTHLY;

    private Date startDate;

    private Date endDate;

    private double monthlyFee;

    private double includedHours;

    private double remainingHours;

    private double retainerBalance;

    private Double hourlyRateOverride;

    private double partsMarkupPercent;

    private boolean autoInvoice;

    /** Last time a periodic invoice was generated from this contract (null if never). */
    private Date lastInvoicedAt;
}
