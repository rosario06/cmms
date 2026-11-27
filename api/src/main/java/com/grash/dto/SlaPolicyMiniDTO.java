package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Minimal SLA policy details")
public class SlaPolicyMiniDTO {
    @Schema(description = "Unique identifier", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "Policy name")
    private String name;
}
