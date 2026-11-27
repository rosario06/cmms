package com.grash.dto;

import com.grash.model.Customer;
import com.grash.model.enums.BillingCycle;
import com.grash.model.enums.ContractStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@Schema(description = "DTO for creating a service contract")
public class ServiceContractPostDTO {
    @Schema(description = "Contract number. Generated per company (CT-YYYY-NNNN) when empty")
    private String contractNumber;

    @NotBlank
    @Schema(description = "Contract name", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Customer the contract belongs to", implementation = IdDTO.class)
    private Customer customer;

    @Schema(description = "Contract status, DRAFT when empty")
    private ContractStatus status;

    @Schema(description = "Billing cycle, MONTHLY when empty")
    private BillingCycle billingCycle;

    private Date startDate;

    private Date endDate;

    @PositiveOrZero
    private double monthlyFee;

    @PositiveOrZero
    private double includedHours;

    @Schema(description = "Hours still available. Equals includedHours when empty")
    @PositiveOrZero
    private Double remainingHours;

    @PositiveOrZero
    private double retainerBalance;

    @PositiveOrZero
    private Double hourlyRateOverride;

    @PositiveOrZero
    private double partsMarkupPercent;

    private boolean autoInvoice;
}
