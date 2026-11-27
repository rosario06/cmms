package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.Priority;
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

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"serviceContract"})
public class SlaPolicy extends CompanyAudit {
    @NotNull
    @Column(nullable = false)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority = Priority.NONE;

    private int firstResponseMinutes;

    private int resolutionMinutes;

    private double penaltyPercent;

    @ManyToOne(fetch = FetchType.LAZY)
    private ServiceContract serviceContract;
}
