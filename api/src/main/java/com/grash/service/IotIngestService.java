package com.grash.service;

import com.grash.dto.integration.IotTelemetryPostDTO;
import com.grash.exception.CustomException;
import com.grash.model.Asset;
import com.grash.model.Company;
import com.grash.model.IntegrationConfig;
import com.grash.model.IotTelemetryLog;
import com.grash.model.Meter;
import com.grash.model.enums.IotTelemetryStatus;
import com.grash.repository.AssetRepository;
import com.grash.repository.IntegrationConfigRepository;
import com.grash.repository.IotTelemetryLogRepository;
import com.grash.repository.MeterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ingestion of IoT telemetry. There is no authenticated user: the company is resolved from the token hash and
 * every lookup is explicitly scoped to that company.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IotIngestService {

    static final int MAX_MESSAGE_LENGTH = 1000;
    private static final long MAX_FUTURE_SKEW_SECONDS = 300;

    private final IntegrationConfigRepository integrationConfigRepository;
    private final IotTelemetryLogRepository iotTelemetryLogRepository;
    private final AssetRepository assetRepository;
    private final MeterRepository meterRepository;
    private final ReadingService readingService;
    private final IotAlertService iotAlertService;

    /**
     * @throws CustomException 401 if the token is missing, unknown or the integration is disabled (nothing logged)
     */
    public IotTelemetryLog ingest(String token, IotTelemetryPostDTO dto) {
        IntegrationConfig config = authenticate(token);
        Company company = config.getCompany();

        Instant timestamp;
        try {
            timestamp = parseTimestamp(dto.getTimestamp());
        } catch (DateTimeParseException e) {
            return saveLog(company, dto, null, IotTelemetryStatus.REJECTED,
                    "timestamp must be an ISO-8601 date-time");
        }
        if (timestamp.isAfter(Instant.now().plusSeconds(MAX_FUTURE_SKEW_SECONDS))) {
            return saveLog(company, dto, timestamp, IotTelemetryStatus.REJECTED, "timestamp is in the future");
        }

        Optional<Asset> asset = assetRepository.findFirstByCustomIdAndCompany_Id(dto.getAssetCode(),
                company.getId());
        if (asset.isEmpty()) {
            return saveLog(company, dto, timestamp, IotTelemetryStatus.ASSET_NOT_FOUND, "Asset not found");
        }

        Optional<Meter> meter = selectMeter(asset.get(), company.getId(), dto.getMetricName());
        if (meter.isEmpty()) {
            return saveLog(company, dto, timestamp, IotTelemetryStatus.METER_NOT_FOUND,
                    "No meter matches the metric on this asset");
        }

        int fired;
        try {
            fired = readingService.createFromTelemetry(meter.get().getId(), dto.getValue());
        } catch (CustomException e) {
            return saveLog(company, dto, timestamp, IotTelemetryStatus.REJECTED, e.getMessage());
        } catch (RuntimeException e) {
            log.error("IoT telemetry processing failed for company {}", company.getId(), e);
            return saveLog(company, dto, timestamp, IotTelemetryStatus.REJECTED, "Processing error");
        }

        if (fired > 0) {
            String alertUrl = config.getWebhookAlertUrl();
            if (alertUrl != null && !alertUrl.isBlank()) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("assetCode", dto.getAssetCode());
                payload.put("metricName", dto.getMetricName());
                payload.put("value", dto.getValue());
                payload.put("timestamp", timestamp.toString());
                iotAlertService.sendAlert(alertUrl, payload);
            }
            return saveLog(company, dto, timestamp, IotTelemetryStatus.TRIGGERED,
                    fired + " meter trigger(s) fired");
        }
        return saveLog(company, dto, timestamp, IotTelemetryStatus.PROCESSED, null);
    }

    private IntegrationConfig authenticate(String token) {
        if (token == null || token.isBlank() || token.length() > 200) {
            throw unauthorized();
        }
        return integrationConfigRepository.findByIotTokenHashAndIotEnabledTrue(IntegrationService.hash(token))
                .orElseThrow(IotIngestService::unauthorized);
    }

    private static CustomException unauthorized() {
        return new CustomException("Unauthorized", HttpStatus.UNAUTHORIZED);
    }

    Optional<Meter> selectMeter(Asset asset, Long companyId, String metricName) {
        Collection<Meter> meters = meterRepository.findByAsset_Id(asset.getId());
        List<Meter> ofCompany = meters.stream()
                .filter(m -> m.getCompany() != null && companyId.equals(m.getCompany().getId()))
                .toList();
        Optional<Meter> byName = ofCompany.stream()
                .filter(m -> m.getName() != null && m.getName().equalsIgnoreCase(metricName))
                .findFirst();
        if (byName.isPresent()) return byName;
        return ofCompany.size() == 1 ? Optional.of(ofCompany.get(0)) : Optional.empty();
    }

    private static Instant parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) return Instant.now();
        try {
            return Instant.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return OffsetDateTime.parse(raw.trim()).toInstant();
        }
    }

    private IotTelemetryLog saveLog(Company company, IotTelemetryPostDTO dto, Instant timestamp,
                                    IotTelemetryStatus status, String message) {
        IotTelemetryLog entry = new IotTelemetryLog();
        entry.setCompany(company);
        entry.setAssetCode(dto.getAssetCode());
        entry.setMetricName(dto.getMetricName());
        entry.setValue(dto.getValue());
        entry.setReadingTimestamp(timestamp == null ? null : Date.from(timestamp));
        entry.setStatus(status);
        if (message != null) {
            entry.setMessage(message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) :
                    message);
        }
        return iotTelemetryLogRepository.save(entry);
    }
}
