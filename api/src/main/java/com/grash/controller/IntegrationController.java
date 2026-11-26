package com.grash.controller;

import com.grash.dto.integration.IntegrationShowDTO;
import com.grash.dto.integration.IotIntegrationPatchDTO;
import com.grash.dto.integration.IotIntegrationShowDTO;
import com.grash.dto.integration.IotTelemetryLogShowDTO;
import com.grash.dto.integration.IotTokenDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.IntegrationMapper;
import com.grash.model.IntegrationConfig;
import com.grash.model.User;
import com.grash.model.enums.RoleCode;
import com.grash.security.CurrentUser;
import com.grash.service.IntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/integrations")
@RequiredArgsConstructor
@Tag(name = "Integrations", description = "Manage the integrations of the company (admins only)")
public class IntegrationController {

    private static final String INGEST_PATH = "/public/iot/telemetry";

    private final IntegrationService integrationService;
    private final IntegrationMapper integrationMapper;

    @Value("${api.host:}")
    private String publicApiUrl;

    @GetMapping
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    @Operation(summary = "Get the integrations status of the company")
    public ResponseEntity<IntegrationShowDTO> get(@Parameter(hidden = true) @CurrentUser User user) {
        requireAdmin(user);
        IntegrationConfig config = integrationService.getOrCreate(user.getCompany());
        return ResponseEntity.ok(new IntegrationShowDTO(toIotDto(config)));
    }

    @PatchMapping("/iot")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    @Operation(summary = "Update the IoT integration settings")
    public ResponseEntity<IotIntegrationShowDTO> patchIot(@Parameter(hidden = true) @CurrentUser User user,
                                                          @Valid @RequestBody IotIntegrationPatchDTO patch) {
        requireAdmin(user);
        IntegrationConfig config = integrationService.updateIot(user.getCompany(), patch);
        return ResponseEntity.ok(toIotDto(config));
    }

    @PostMapping("/iot/token")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    @Operation(summary = "Generate (rotate) the IoT ingestion token",
            description = "The token is returned only once. The previous token stops working.")
    public ResponseEntity<IotTokenDTO> rotateToken(@Parameter(hidden = true) @CurrentUser User user) {
        requireAdmin(user);
        return ResponseEntity.ok(integrationService.rotateIotToken(user.getCompany()));
    }

    @GetMapping("/iot/telemetry")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    @Operation(summary = "Latest IoT telemetry received (max 100)")
    public ResponseEntity<List<IotTelemetryLogShowDTO>> getTelemetry(
            @Parameter(hidden = true) @CurrentUser User user,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        requireAdmin(user);
        return ResponseEntity.ok(integrationService.getLatestTelemetry(user.getCompany().getId(), size).stream()
                .map(integrationMapper::toTelemetryShowDto)
                .toList());
    }

    private void requireAdmin(User user) {
        if (user.getRole() == null || user.getRole().getCode() != RoleCode.ADMIN) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }

    private IotIntegrationShowDTO toIotDto(IntegrationConfig config) {
        IotIntegrationShowDTO dto = integrationMapper.toIotShowDto(config);
        String base = publicApiUrl == null ? "" : publicApiUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        dto.setIngestUrl(base + INGEST_PATH);
        return dto;
    }
}
