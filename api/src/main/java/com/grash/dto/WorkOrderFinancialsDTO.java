package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Cost, billable amount and margin breakdown of a work order")
public class WorkOrderFinancialsDTO {
    private double laborInternal;
    private double laborBillable;
    private double partsInternal;
    private double partsBillable;
    private double additionalInternal;
    private double additionalBillable;
    private double totalInternalCost;
    private double totalBillableAmount;
    private double netMarginAmount;
    private double marginPercentage;
    private double contractCoveredHours;
}
