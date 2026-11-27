package com.grash.mapper;

import com.grash.dto.SlaPolicyMiniDTO;
import com.grash.dto.SlaPolicyPatchDTO;
import com.grash.dto.SlaPolicyPostDTO;
import com.grash.dto.SlaPolicyShowDTO;
import com.grash.model.SlaPolicy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", uses = {ServiceContractMapper.class, WorkScheduleMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SlaPolicyMapper {
    // the contract is validated against the company and set by the service
    @Mappings({@Mapping(target = "serviceContract", ignore = true), @Mapping(target = "workSchedule", ignore = true)})
    SlaPolicy updateSlaPolicy(@MappingTarget SlaPolicy entity, SlaPolicyPatchDTO dto);

    @Mappings({@Mapping(target = "serviceContract", ignore = true), @Mapping(target = "workSchedule", ignore = true)})
    SlaPolicy fromPostDto(SlaPolicyPostDTO dto);

    SlaPolicyShowDTO toShowDto(SlaPolicy model);

    SlaPolicyMiniDTO toMiniDto(SlaPolicy model);
}
