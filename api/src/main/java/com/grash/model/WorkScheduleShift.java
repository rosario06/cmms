package com.grash.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.grash.model.abstracts.CompanyAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalTime;

/**
 * One working span of a {@link WorkSchedule}. When endTime is not after startTime the span crosses midnight and ends
 * the next day (night shift); it belongs to the day it starts.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"schedule"})
@ToString(callSuper = true, exclude = {"schedule"})
public class WorkScheduleShift extends CompanyAudit {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    @JsonIgnore
    private WorkSchedule schedule;

    private String name;

    /** ISO day of week: 1 = Monday ... 7 = Sunday. */
    @Column(nullable = false)
    private int dayOfWeek;

    @NotNull
    @Column(nullable = false)
    private LocalTime startTime;

    @NotNull
    @Column(nullable = false)
    private LocalTime endTime;
}
