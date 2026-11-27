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

import java.time.LocalDate;

@Entity
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"workSchedule"})
@ToString(callSuper = true, exclude = {"workSchedule"})
public class Holiday extends CompanyAudit {
    @NotNull
    @Column(nullable = false)
    private String name;

    @NotNull
    @Column(name = "holiday_date", nullable = false)
    private LocalDate date;

    /** When true the holiday repeats every year on the same month and day. */
    private boolean recurringAnnually;

    /** Null means the holiday applies to every schedule of the company. */
    @ManyToOne(fetch = FetchType.LAZY)
    private WorkSchedule workSchedule;
}
