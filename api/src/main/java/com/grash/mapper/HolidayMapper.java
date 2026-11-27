package com.grash.mapper;

import com.grash.dto.HolidayPatchDTO;
import com.grash.dto.HolidayPostDTO;
import com.grash.dto.HolidayShowDTO;
import com.grash.model.Holiday;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", uses = {WorkScheduleMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface HolidayMapper {
    // the schedule is validated against the company and set by the service
    @Mappings({@Mapping(target = "workSchedule", ignore = true)})
    Holiday updateHoliday(@MappingTarget Holiday entity, HolidayPatchDTO dto);

    @Mappings({@Mapping(target = "workSchedule", ignore = true)})
    Holiday fromPostDto(HolidayPostDTO dto);

    HolidayShowDTO toShowDto(Holiday model);
}
