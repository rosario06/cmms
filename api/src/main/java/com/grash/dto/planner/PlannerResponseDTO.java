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
@Schema(description = "Planner data: scheduled work orders in the range and unassigned/unscheduled ones")
public class PlannerResponseDTO {
    private List<PlannerEventDTO> scheduled = new ArrayList<>();
    private List<PlannerEventDTO> unassigned = new ArrayList<>();
}
