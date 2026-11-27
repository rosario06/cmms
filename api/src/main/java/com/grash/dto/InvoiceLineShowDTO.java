package com.grash.dto;

import com.grash.model.enums.InvoiceItemType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Invoice line returned by the API")
public class InvoiceLineShowDTO {
    private Long id;
    private String description;
    private InvoiceItemType itemType;
    private double quantity;
    private double unitPrice;
    private double lineTotal;
}
