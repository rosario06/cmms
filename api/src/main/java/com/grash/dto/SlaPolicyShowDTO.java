package com.grash.dto;

import com.grash.model.enums.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "DTO for displaying an SLA policy")
public class SlaPolicyShowDTO extends AuditShowDTO {
    private String name;
    private Priority priority;
    private int firstResponseMinutes;
    private int resolutionMinutes;
    private double penaltyPercent;
    private ServiceContractMiniDTO serviceContract;
}
