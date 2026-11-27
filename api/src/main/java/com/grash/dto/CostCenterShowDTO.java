package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "DTO for displaying a cost center in API responses")
public class CostCenterShowDTO extends AuditShowDTO {

    @Schema(description = "Cost center code")
    private String code;

    @Schema(description = "Cost center name")
    private String name;

    @Schema(description = "Customer")
    private CustomerMiniDTO customer;

    @Schema(description = "Location")
    private LocationMiniDTO location;

    @Schema(description = "Fiscal period")
    private String fiscalPeriod;

    @Schema(description = "Allocated budget")
    private double allocatedBudget;

    @Schema(description = "Amount spent (sum of the internal cost of its work orders)")
    private double spentAmount;

    @Schema(description = "Percentage of the budget that triggers an alert")
    private double alertThresholdPercent;

    @Schema(description = "Whether the cost center is active")
    private boolean active;

    @Schema(description = "Whether the threshold alert was already fired")
    private boolean alertSent;
}
