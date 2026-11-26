package com.grash.controller;

import com.grash.dto.integration.IotTelemetryPostDTO;
import com.grash.dto.integration.IotTelemetryResultDTO;
import com.grash.model.IotTelemetryLog;
import com.grash.service.IotIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public/iot")
@RequiredArgsConstructor
@Tag(name = "IoT ingestion", description = "Public endpoint for IoT gateways, authenticated with X-IoT-Token")
public class PublicIotController {

    private final IotIngestService iotIngestService;

    @PostMapping("/telemetry")
    @Operation(summary = "Ingest a telemetry value")
    public ResponseEntity<IotTelemetryResultDTO> ingest(
            @Parameter(description = "IoT token generated in Integrations settings")
            @RequestHeader(value = "X-IoT-Token", required = false) String token,
            @Valid @RequestBody IotTelemetryPostDTO body) {
        // The company must come exclusively from the token, never from a JWT that might accompany the request
        SecurityContextHolder.clearContext();
        IotTelemetryLog result = iotIngestService.ingest(token, body);
        HttpStatus httpStatus = switch (result.getStatus()) {
            case PROCESSED, TRIGGERED -> HttpStatus.OK;
            case ASSET_NOT_FOUND, METER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case REJECTED -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(httpStatus)
                .body(new IotTelemetryResultDTO(result.getStatus(), result.getMessage()));
    }
}
