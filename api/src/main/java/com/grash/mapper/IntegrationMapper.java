package com.grash.mapper;

import com.grash.dto.integration.IotIntegrationShowDTO;
import com.grash.dto.integration.IotTelemetryLogShowDTO;
import com.grash.model.IntegrationConfig;
import com.grash.model.IotTelemetryLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface IntegrationMapper {
    @Mapping(source = "iotEnabled", target = "enabled")
    @Mapping(target = "hasToken",
            expression = "java(model.getIotTokenHash() != null && !model.getIotTokenHash().isEmpty())")
    @Mapping(source = "iotTokenLast4", target = "tokenLast4")
    @Mapping(target = "ingestUrl", ignore = true)
    IotIntegrationShowDTO toIotShowDto(IntegrationConfig model);

    @Mapping(source = "readingTimestamp", target = "timestamp")
    IotTelemetryLogShowDTO toTelemetryShowDto(IotTelemetryLog model);
}
