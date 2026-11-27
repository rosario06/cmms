package com.grash.dto.planner;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@Schema(description = "Partial scheduling update of a work order (only present fields are applied)")
public class WorkOrderScheduleDTO {
    private Date dueDate;
    private Date estimatedStartDate;

    @Min(1)
    @Max(1440 * 14)
    @Schema(description = "Estimated duration in minutes")
    private Integer estimatedDurationMinutes;

    private Long primaryUserId;

    /**
     * Distinguishes an absent primaryUserId (no change) from an explicit null (unassign).
     */
    @JsonIgnore
    private boolean primaryUserIdPresent;

    public void setPrimaryUserId(Long primaryUserId) {
        this.primaryUserId = primaryUserId;
        this.primaryUserIdPresent = true;
    }
}
