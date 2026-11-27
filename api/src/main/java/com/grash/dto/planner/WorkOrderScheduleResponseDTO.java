package com.grash.dto.planner;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of a planner scheduling update")
public class WorkOrderScheduleResponseDTO {
    private PlannerEventDTO event;
    private List<String> warnings = new ArrayList<>();
    private List<Long> conflictingWorkOrderIds = new ArrayList<>();
}
