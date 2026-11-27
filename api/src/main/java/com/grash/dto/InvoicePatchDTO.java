package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@Schema(description = "Fields editable while an invoice is a draft (null fields are left unchanged)")
public class InvoicePatchDTO {
    @Valid
    @Schema(description = "Replaces all the lines when not null")
    private List<InvoiceLinePostDTO> lines;

    @Schema(description = "Notes")
    private String notes;

    @Schema(description = "Due date")
    private Date dueDate;

    @Schema(description = "Tax rate in percent")
    private Double taxRatePercent;

    @Schema(description = "Currency code")
    private String currency;
}
