package com.grash.dto;

import com.grash.model.Customer;
import com.grash.model.Location;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "DTO for creating a new cost center")
public class CostCenterPostDTO {

    @NotBlank
    @Schema(description = "Cost center code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @NotBlank
    @Schema(description = "Cost center name", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Customer charged by this cost center", implementation = IdDTO.class)
    private Customer customer;

    @Schema(description = "Location of this cost center", implementation = IdDTO.class)
    private Location location;

    @Schema(description = "Fiscal period, free text (e.g. 2026 or 2026-Q1)")
    private String fiscalPeriod;

    @Schema(description = "Allocated budget")
    private double allocatedBudget;

    @Schema(description = "Percentage of the budget that triggers an alert")
    private double alertThresholdPercent = 80;

    @Schema(description = "Whether the cost center is active")
    private boolean active = true;
}
