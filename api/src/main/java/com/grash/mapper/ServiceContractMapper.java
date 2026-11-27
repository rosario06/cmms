package com.grash.mapper;

import com.grash.dto.ServiceContractMiniDTO;
import com.grash.dto.ServiceContractPatchDTO;
import com.grash.dto.ServiceContractPostDTO;
import com.grash.dto.ServiceContractShowDTO;
import com.grash.model.ServiceContract;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", uses = {CustomerMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ServiceContractMapper {
    // the customer is validated against the company and set by the service
    @Mappings({@Mapping(target = "customer", ignore = true)})
    ServiceContract updateServiceContract(@MappingTarget ServiceContract entity, ServiceContractPatchDTO dto);

    @Mappings({@Mapping(target = "customer", ignore = true)})
    ServiceContract fromPostDto(ServiceContractPostDTO dto);

    ServiceContractShowDTO toShowDto(ServiceContract model);

    ServiceContractMiniDTO toMiniDto(ServiceContract model);
}
