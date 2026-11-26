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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IotIngestServiceTest {

    private static final String TOKEN = "iot_live_test-token";

    @Mock
    private IntegrationConfigRepository integrationConfigRepository;
    @Mock
    private IotTelemetryLogRepository iotTelemetryLogRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private MeterRepository meterRepository;
    @Mock
    private ReadingService readingService;
    @Mock
    private IotAlertService iotAlertService;
    @InjectMocks
    private IotIngestService service;

    private Company company;
    private IntegrationConfig config;
    private Asset asset;
    private IotTelemetryPostDTO dto;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);
        config = new IntegrationConfig();
        config.setCompany(company);
        config.setIotEnabled(true);
        asset = new Asset();
        asset.setId(10L);
        dto = new IotTelemetryPostDTO();
        dto.setAssetCode("AST-1");
        dto.setMetricName("Temperature");
        dto.setValue(42.5);
        lenient().when(iotTelemetryLogRepository.save(any(IotTelemetryLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private void authenticated() {
        when(integrationConfigRepository.findByIotTokenHashAndIotEnabledTrue(IntegrationService.hash(TOKEN)))
                .thenReturn(Optional.of(config));
    }

    private Meter meter(Long id, String name) {
        Meter meter = new Meter();
        meter.setId(id);
        meter.setName(name);
        meter.setCompany(company);
        return meter;
    }

    @Test
    void ingest_unknownTokenOrDisabled_throws401AndLogsNothing() {
        when(integrationConfigRepository.findByIotTokenHashAndIotEnabledTrue(anyString()))
                .thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> service.ingest(TOKEN, dto));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
        verifyNoInteractions(iotTelemetryLogRepository, assetRepository, readingService);
    }

    @Test
    void ingest_missingToken_throws401() {
        CustomException ex = assertThrows(CustomException.class, () -> service.ingest(null, dto));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
        verifyNoInteractions(integrationConfigRepository, iotTelemetryLogRepository);
    }

    @Test
    void ingest_assetNotFound_logsAssetNotFound() {
        authenticated();
        when(assetRepository.findFirstByCustomIdAndCompany_Id("AST-1", 1L)).thenReturn(Optional.empty());

        IotTelemetryLog result = service.ingest(TOKEN, dto);

        assertEquals(IotTelemetryStatus.ASSET_NOT_FOUND, result.getStatus());
        assertEquals(company, result.getCompany());
        verifyNoInteractions(readingService);
    }

    @Test
    void ingest_noMeterMatches_logsMeterNotFound() {
        authenticated();
        when(assetRepository.findFirstByCustomIdAndCompany_Id("AST-1", 1L)).thenReturn(Optional.of(asset));
        when(meterRepository.findByAsset_Id(10L)).thenReturn(List.of(meter(1L, "Pressure"), meter(2L, "Voltage")));

        IotTelemetryLog result = service.ingest(TOKEN, dto);

        assertEquals(IotTelemetryStatus.METER_NOT_FOUND, result.getStatus());
        verifyNoInteractions(readingService);
    }

    @Test
    void ingest_matchesMeterByNameIgnoringCase_processed() {
        authenticated();
        when(assetRepository.findFirstByCustomIdAndCompany_Id("AST-1", 1L)).thenReturn(Optional.of(asset));
        when(meterRepository.findByAsset_Id(10L)).thenReturn(List.of(meter(1L, "Pressure"), meter(2L, "TEMPERATURE")));
        when(readingService.createFromTelemetry(2L, 42.5)).thenReturn(0);

        IotTelemetryLog result = service.ingest(TOKEN, dto);

        assertEquals(IotTelemetryStatus.PROCESSED, result.getStatus());
        assertEquals(Double.valueOf(42.5), result.getValue());
        verify(readingService).createFromTelemetry(2L, 42.5);
        verify(iotAlertService, never()).sendAlert(any(), any());
    }

    @Test
    void ingest_singleMeterWithoutNameMatch_usesIt() {
        authenticated();
        when(assetRepository.findFirstByCustomIdAndCompany_Id("AST-1", 1L)).thenReturn(Optional.of(asset));
        when(meterRepository.findByAsset_Id(10L)).thenReturn(List.of(meter(5L, "Only meter")));
        when(readingService.createFromTelemetry(5L, 42.5)).thenReturn(0);

        IotTelemetryLog result = service.ingest(TOKEN, dto);

        assertEquals(IotTelemetryStatus.PROCESSED, result.getStatus());
    }

    @Test
    void ingest_triggerFired_logsTriggeredAndSendsAlert() {
        config.setWebhookAlertUrl("https://example.com/hook");
        authenticated();
        when(assetRepository.findFirstByCustomIdAndCompany_Id("AST-1", 1L)).thenReturn(Optional.of(asset));
        when(meterRepository.findByAsset_Id(10L)).thenReturn(List.of(meter(2L, "Temperature")));
        when(readingService.createFromTelemetry(2L, 42.5)).thenReturn(1);
        dto.setTimestamp("2026-01-01T10:00:00Z");

        IotTelemetryLog result = service.ingest(TOKEN, dto);

        assertEquals(IotTelemetryStatus.TRIGGERED, result.getStatus());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(iotAlertService).sendAlert(eq("https://example.com/hook"), captor.capture());
        assertEquals("AST-1", captor.getValue().get("assetCode"));
        assertEquals("Temperature", captor.getValue().get("metricName"));
        assertEquals(42.5, captor.getValue().get("value"));
        assertEquals("2026-01-01T10:00:00Z", captor.getValue().get("timestamp"));
    }

    @Test
    void ingest_triggerFiredWithoutAlertUrl_noAlert() {
        authenticated();
        when(assetRepository.findFirstByCustomIdAndCompany_Id("AST-1", 1L)).thenReturn(Optional.of(asset));
        when(meterRepository.findByAsset_Id(10L)).thenReturn(List.of(meter(2L, "Temperature")));
        when(readingService.createFromTelemetry(2L, 42.5)).thenReturn(2);

        IotTelemetryLog result = service.ingest(TOKEN, dto);

        assertEquals(IotTelemetryStatus.TRIGGERED, result.getStatus());
        verify(iotAlertService, never()).sendAlert(any(), anyMap());
    }

    @Test
    void ingest_readingRejected_logsRejected() {
        authenticated();
        when(assetRepository.findFirstByCustomIdAndCompany_Id("AST-1", 1L)).thenReturn(Optional.of(asset));
        when(meterRepository.findByAsset_Id(10L)).thenReturn(List.of(meter(2L, "Temperature")));
        when(readingService.createFromTelemetry(anyLong(), anyDouble()))
                .thenThrow(new CustomException("Access denied", HttpStatus.FORBIDDEN));

        IotTelemetryLog result = service.ingest(TOKEN, dto);

        assertEquals(IotTelemetryStatus.REJECTED, result.getStatus());
        assertEquals("Access denied", result.getMessage());
    }

    @Test
    void ingest_invalidTimestamp_logsRejected() {
        authenticated();
        dto.setTimestamp("yesterday");

        IotTelemetryLog result = service.ingest(TOKEN, dto);

        assertEquals(IotTelemetryStatus.REJECTED, result.getStatus());
        verifyNoInteractions(assetRepository, readingService);
    }
}
