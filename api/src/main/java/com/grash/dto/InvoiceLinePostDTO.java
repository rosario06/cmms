package com.grash.dto;

import com.grash.model.enums.InvoiceItemType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Invoice line sent when editing a draft invoice")
public class InvoiceLinePostDTO {
    @Schema(description = "Line description")
    private String description;

    @NotNull
    @Schema(description = "Line type", requiredMode = Schema.RequiredMode.REQUIRED)
    private InvoiceItemType itemType;

    @Schema(description = "Quantity (hours, units...)")
    private double quantity;

    @Schema(description = "Unit price")
    private double unitPrice;
}
