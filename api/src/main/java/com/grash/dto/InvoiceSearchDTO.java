package com.grash.dto;

import com.grash.model.enums.InvoiceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Sort;

import java.util.Date;

@Data
@NoArgsConstructor
@Schema(description = "Paginated invoice search")
public class InvoiceSearchDTO {
    private InvoiceStatus status;
    private Long customerId;
    @Schema(description = "Issue date from (inclusive)")
    private Date startDate;
    @Schema(description = "Issue date to (inclusive)")
    private Date endDate;
    @Schema(description = "Text contained in the invoice number")
    private String text;
    private int pageNum = 0;
    private int pageSize = 10;
    @Schema(description = "One of id, invoiceNumber, issueDate, dueDate, status, totalAmount, createdAt")
    private String sortField = "id";
    private Sort.Direction direction = Sort.Direction.DESC;
}
