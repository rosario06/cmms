package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Minimal work schedule details")
public class WorkScheduleMiniDTO {
    @Schema(description = "Unique identifier", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "Schedule name")
    private String name;

    @Schema(description = "Whether it is the default schedule of the company")
    private boolean defaultSchedule;
}
