package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Schema(description = "DTO for creating a work schedule")
public class WorkSchedulePostDTO {
    @NotBlank
    @Schema(description = "Schedule name", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank
    @Schema(description = "IANA time zone, e.g. America/Santo_Domingo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String timezone;

    @Schema(description = "Default schedule of the company (only one: the others are unmarked)")
    private boolean defaultSchedule;

    @Schema(description = "Whether the schedule is in use, true when empty")
    private Boolean active;

    @Valid
    @Schema(description = "Working spans of the week; they cannot overlap")
    private List<WorkScheduleShiftDTO> shifts = new ArrayList<>();
}
