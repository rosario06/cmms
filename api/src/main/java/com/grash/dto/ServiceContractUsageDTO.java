package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Consumption of the hours and retainer of a service contract")
public class ServiceContractUsageDTO {
    private double includedHours;
    private double remainingHours;
    @Schema(description = "includedHours - remainingHours, never negative")
    private double consumedHours;
    @Schema(description = "consumedHours / includedHours * 100 (0 when there are no included hours)")
    private double consumedPercent;
    private double retainerBalance;
}
