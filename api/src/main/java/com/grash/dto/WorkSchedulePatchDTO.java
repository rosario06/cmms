package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Every field is optional: a null field is left unchanged. When shifts is present (even empty) it replaces ALL the
 * shifts of the schedule.
 */
@Data
@NoArgsConstructor
@Schema(description = "DTO for patching a work schedule (null fields are not modified)")
public class WorkSchedulePatchDTO {
    private String name;

    @Schema(description = "IANA time zone, e.g. America/Santo_Domingo")
    private String timezone;

    private Boolean defaultSchedule;

    private Boolean active;

    @Valid
    @Schema(description = "When present, replaces all the shifts")
    private List<WorkScheduleShiftDTO> shifts;
}
