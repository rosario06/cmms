package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Minimal service contract details")
public class ServiceContractMiniDTO {
    @Schema(description = "Unique identifier", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "Contract name")
    private String name;

    @Schema(description = "Contract number, unique per company")
    private String contractNumber;
}
