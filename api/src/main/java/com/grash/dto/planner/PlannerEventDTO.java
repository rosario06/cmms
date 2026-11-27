package com.grash.dto.planner;

import com.grash.model.enums.Priority;
import com.grash.model.enums.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Work order as displayed in the visual planner")
public class PlannerEventDTO {
    private Long id;
    private String title;
    private Priority priority;
    private Status status;
    private Date dueDate;
    private Date estimatedStartDate;
    @Schema(description = "Estimated duration in minutes (null when not set)")
    private Long estimatedDurationMinutes;
    private Long primaryUserId;
    private String primaryUserName;
    private String assetName;
    private String locationName;
}
