package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"customer", "location"})
public class CostCenter extends CompanyAudit {
    @NotNull
    @Column(nullable = false)
    private String code;

    @NotNull
    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    private Location location;

    /** Free text, e.g. "2026" or "2026-Q1". */
    private String fiscalPeriod;

    private double allocatedBudget;

    private double spentAmount;

    private double alertThresholdPercent = 80;

    private boolean active = true;

    /** True once the alert for the current threshold was fired, to avoid repeating it on each recalculation. */
    private boolean alertSent;
}
