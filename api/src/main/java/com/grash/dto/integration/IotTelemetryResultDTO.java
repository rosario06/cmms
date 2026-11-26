package com.grash.dto.integration;

import com.grash.model.enums.IotTelemetryStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IotTelemetryResultDTO {
    private IotTelemetryStatus status;
    private String message;
}
