package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "DTO for displaying a work schedule")
public class WorkScheduleShowDTO extends AuditShowDTO {
    private String name;
    private String timezone;
    private boolean defaultSchedule;
    private boolean active;
    private List<WorkScheduleShiftDTO> shifts = new ArrayList<>();
}
