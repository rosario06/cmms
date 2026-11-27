package com.grash.mapper;

import com.grash.dto.CostCenterPatchDTO;
import com.grash.dto.CostCenterPostDTO;
import com.grash.dto.CostCenterShowDTO;
import com.grash.model.CostCenter;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", uses = {CustomerMapper.class, LocationMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CostCenterMapper {
    CostCenter updateCostCenter(@MappingTarget CostCenter entity, CostCenterPatchDTO dto);

    CostCenter fromPostDto(CostCenterPostDTO dto);

    CostCenterShowDTO toShowDto(CostCenter model);
}
