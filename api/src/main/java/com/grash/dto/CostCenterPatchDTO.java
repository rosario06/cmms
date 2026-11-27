package com.grash.dto;

import com.grash.model.Customer;
import com.grash.model.Location;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "DTO for patching a cost center (null fields are left unchanged)")
public class CostCenterPatchDTO {

    @Schema(description = "Cost center code")
    private String code;

    @Schema(description = "Cost center name")
    private String name;

    @Schema(description = "Customer charged by this cost center", implementation = IdDTO.class)
    private Customer customer;

    @Schema(description = "Location of this cost center", implementation = IdDTO.class)
    private Location location;

    @Schema(description = "Fiscal period, free text")
    private String fiscalPeriod;

    @Schema(description = "Allocated budget")
    private Double allocatedBudget;

    @Schema(description = "Percentage of the budget that triggers an alert")
    private Double alertThresholdPercent;

    @Schema(description = "Whether the cost center is active")
    private Boolean active;
}
