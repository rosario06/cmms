package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Cost center reference shown inside a work order")
public class WorkOrderCostCenterDTO {
    @Schema(description = "Unique identifier", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "Cost center code")
    private String code;

    @Schema(description = "Cost center name")
    private String name;
}
