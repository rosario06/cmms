package com.grash.dto;

import com.grash.model.ServiceContract;
import com.grash.model.enums.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "DTO for creating an SLA policy")
public class SlaPolicyPostDTO {
    @NotBlank
    @Schema(description = "Policy name", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Work order priority this policy applies to, NONE when empty")
    private Priority priority;

    @Schema(description = "Minutes (wall clock, no business hours) to the first response. 0 = no target")
    @PositiveOrZero
    private int firstResponseMinutes;

    @Schema(description = "Minutes (wall clock, no business hours) to the resolution. 0 = no target")
    @PositiveOrZero
    private int resolutionMinutes;

    @PositiveOrZero
    @DecimalMax("100")
    private double penaltyPercent;

    @Schema(description = "Contract of the policy. Empty means a general policy of the company", implementation =
            IdDTO.class)
    private ServiceContract serviceContract;
}
