package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Invoicing totals")
public class InvoiceSummaryDTO {
    @Schema(description = "Sum of ISSUED + PAID + OVERDUE invoices")
    private double totalInvoiced;
    @Schema(description = "Sum of ISSUED + OVERDUE invoices")
    private double pendingCollection;
    @Schema(description = "Sum of OVERDUE invoices")
    private double overdue;
    @Schema(description = "Sum of PAID invoices")
    private double paid;
    @Schema(description = "Number of invoices counted in totalInvoiced")
    private long count;
}
