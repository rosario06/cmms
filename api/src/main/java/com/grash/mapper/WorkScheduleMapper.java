package com.grash.mapper;

import com.grash.dto.WorkScheduleMiniDTO;
import com.grash.dto.WorkSchedulePatchDTO;
import com.grash.dto.WorkSchedulePostDTO;
import com.grash.dto.WorkScheduleShiftDTO;
import com.grash.dto.WorkScheduleShowDTO;
import com.grash.model.WorkSchedule;
import com.grash.model.WorkScheduleShift;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface WorkScheduleMapper {
    // the shifts are validated (overlaps) and replaced as a whole by the service
    @Mappings({@Mapping(target = "shifts", ignore = true)})
    WorkSchedule updateWorkSchedule(@MappingTarget WorkSchedule entity, WorkSchedulePatchDTO dto);

    @Mappings({@Mapping(target = "shifts", ignore = true)})
    WorkSchedule fromPostDto(WorkSchedulePostDTO dto);

    WorkScheduleShowDTO toShowDto(WorkSchedule model);

    WorkScheduleMiniDTO toMiniDto(WorkSchedule model);

    WorkScheduleShiftDTO toShiftDto(WorkScheduleShift model);
}
