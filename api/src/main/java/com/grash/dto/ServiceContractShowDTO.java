package com.grash.dto;

import com.grash.model.enums.BillingCycle;
import com.grash.model.enums.ContractStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@Schema(description = "DTO for displaying a service contract")
public class ServiceContractShowDTO extends AuditShowDTO {
    private String contractNumber;
    private String name;
    private CustomerMiniDTO customer;
    private ContractStatus status;
    private BillingCycle billingCycle;
    private Date startDate;
    private Date endDate;
    private double monthlyFee;
    private double includedHours;
    private double remainingHours;
    private double retainerBalance;
    private Double hourlyRateOverride;
    private double partsMarkupPercent;
    private boolean autoInvoice;
    private Date lastInvoicedAt;
}
