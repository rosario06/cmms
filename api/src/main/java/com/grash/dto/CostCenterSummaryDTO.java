package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Budget execution summary of a cost center")
public class CostCenterSummaryDTO {
    private double allocatedBudget;
    private double spentAmount;
    private double executionPercent;
    private double remaining;
    private double alertThresholdPercent;
    private boolean overThreshold;
    private long workOrderCount;
}
