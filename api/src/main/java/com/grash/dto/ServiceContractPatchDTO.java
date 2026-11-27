package com.grash.dto;

import com.grash.model.Customer;
import com.grash.model.enums.BillingCycle;
import com.grash.model.enums.ContractStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Every field is optional: a null field is left unchanged.
 */
@Data
@NoArgsConstructor
@Schema(description = "DTO for patching a service contract (null fields are not modified)")
public class ServiceContractPatchDTO {
    private String contractNumber;

    private String name;

    @Schema(description = "Customer; send an object without id to detach the customer", implementation =
            IdDTO.class)
    private Customer customer;

    private ContractStatus status;

    private BillingCycle billingCycle;

    private Date startDate;

    private Date endDate;

    @PositiveOrZero
    private Double monthlyFee;

    @PositiveOrZero
    private Double includedHours;

    @PositiveOrZero
    private Double remainingHours;

    @PositiveOrZero
    private Double retainerBalance;

    @PositiveOrZero
    private Double hourlyRateOverride;

    @PositiveOrZero
    private Double partsMarkupPercent;

    private Boolean autoInvoice;
}
