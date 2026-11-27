package com.grash.dto;

import com.grash.model.enums.InvoiceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Customer invoice returned by the API")
public class InvoiceShowDTO extends AuditShowDTO {
    private String invoiceNumber;
    private CustomerMiniDTO customer;
    private String customerBillingName;
    private Long workOrderId;
    private String workOrderTitle;
    private Long serviceContractId;
    private String contractNumber;
    private Date issueDate;
    private Date dueDate;
    private InvoiceStatus status;
    private String currency;
    private double subtotal;
    private double taxRatePercent;
    private double taxAmount;
    private double totalAmount;
    private String notes;
    private Date paidAt;
    private List<InvoiceLineShowDTO> lines = new ArrayList<>();
}
