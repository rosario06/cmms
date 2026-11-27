package com.grash.dto;

import com.grash.model.ServiceContract;
import com.grash.model.enums.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Every field is optional: a null field is left unchanged.
 */
@Data
@NoArgsConstructor
@Schema(description = "DTO for patching an SLA policy (null fields are not modified)")
public class SlaPolicyPatchDTO {
    private String name;

    private Priority priority;

    @PositiveOrZero
    private Integer firstResponseMinutes;

    @PositiveOrZero
    private Integer resolutionMinutes;

    @PositiveOrZero
    @DecimalMax("100")
    private Double penaltyPercent;

    @Schema(description = "Contract; send an object without id to make the policy general", implementation =
            IdDTO.class)
    private ServiceContract serviceContract;
}
