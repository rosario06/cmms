package com.grash.dto.integration;

import com.grash.model.enums.IotTelemetryStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class IotTelemetryLogShowDTO {
    private Long id;
    private String assetCode;
    private String metricName;
    private Double value;
    private Instant timestamp;
    private IotTelemetryStatus status;
    private String message;
    private Instant createdAt;
}
