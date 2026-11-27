package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Business hours of a company (used by the SLA deadlines). The shifts are weekly: each one belongs to a day of the
 * week and several shifts per day are allowed.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"shifts"})
@ToString(callSuper = true, exclude = {"shifts"})
public class WorkSchedule extends CompanyAudit {
    @NotNull
    @Column(nullable = false)
    private String name;

    /** IANA zone id, e.g. "America/Santo_Domingo". */
    @NotNull
    @Column(nullable = false)
    private String timezone;

    /** At most one per company: marking one unmarks the others. */
    private boolean defaultSchedule;

    private boolean active = true;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("dayOfWeek ASC, startTime ASC")
    private List<WorkScheduleShift> shifts = new ArrayList<>();
}
