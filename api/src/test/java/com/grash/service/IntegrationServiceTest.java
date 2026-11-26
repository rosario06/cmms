package com.grash.service;

import com.grash.dto.integration.IotIntegrationPatchDTO;
import com.grash.dto.integration.IotTokenDTO;
import com.grash.exception.CustomException;
import com.grash.model.Company;
import com.grash.model.IntegrationConfig;
import com.grash.repository.IntegrationConfigRepository;
import com.grash.repository.IotTelemetryLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationServiceTest {

    @Mock
    private IntegrationConfigRepository integrationConfigRepository;
    @Mock
    private IotTelemetryLogRepository iotTelemetryLogRepository;
    @InjectMocks
    private IntegrationService service;

    private Company company;
    private IntegrationConfig config;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);
        config = new IntegrationConfig();
        config.setCompany(company);
        lenient().when(integrationConfigRepository.findByCompanyId(1L)).thenReturn(Optional.of(config));
        lenient().when(integrationConfigRepository.save(any(IntegrationConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void getOrCreate_createsLazilyWhenMissing() {
        when(integrationConfigRepository.findByCompanyId(1L)).thenReturn(Optional.empty());
        when(integrationConfigRepository.saveAndFlush(any(IntegrationConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        IntegrationConfig created = service.getOrCreate(company);

        assertEquals(company, created.getCompany());
        assertFalse(created.isIotEnabled());
    }

    @Test
    void rotateIotToken_storesOnlyHashAndLast4() {
        IotTokenDTO result = service.rotateIotToken(company);

        assertTrue(result.getToken().startsWith("iot_live_"));
        assertEquals(result.getToken().substring(result.getToken().length() - 4), result.getTokenLast4());
        assertEquals(result.getTokenLast4(), config.getIotTokenLast4());
        assertEquals(IntegrationService.hash(result.getToken()), config.getIotTokenHash());
        assertNotEquals(result.getToken(), config.getIotTokenHash());
    }

    @Test
    void rotateIotToken_invalidatesPreviousToken() {
        IotTokenDTO first = service.rotateIotToken(company);
        String firstHash = config.getIotTokenHash();
        IotTokenDTO second = service.rotateIotToken(company);

        assertNotEquals(first.getToken(), second.getToken());
        assertNotEquals(firstHash, config.getIotTokenHash());
    }

    @Test
    void updateIot_enablesAndClearsUrlWhenBlank() {
        config.setWebhookAlertUrl("https://example.com");
        IotIntegrationPatchDTO patch = new IotIntegrationPatchDTO();
        patch.setEnabled(true);
        patch.setWebhookAlertUrl(null);

        IntegrationConfig updated = service.updateIot(company, patch);

        assertTrue(updated.isIotEnabled());
        assertNull(updated.getWebhookAlertUrl());
    }

    @Test
    void updateIot_absentUrlKeepsExisting() {
        config.setWebhookAlertUrl("https://example.com");
        IotIntegrationPatchDTO patch = new IotIntegrationPatchDTO();
        patch.setEnabled(false);

        IntegrationConfig updated = service.updateIot(company, patch);

        assertEquals("https://example.com", updated.getWebhookAlertUrl());
    }

    @Test
    void updateIot_privateUrl_rejectedWith400() {
        IotIntegrationPatchDTO patch = new IotIntegrationPatchDTO();
        patch.setWebhookAlertUrl("http://127.0.0.1/hook");

        CustomException ex = assertThrows(CustomException.class, () -> service.updateIot(company, patch));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void getLatestTelemetry_capsSizeAt100() {
        when(iotTelemetryLogRepository.findByCompanyIdOrderByCreatedAtDescIdDesc(eq(1L), any(Pageable.class)))
                .thenReturn(List.of());

        service.getLatestTelemetry(1L, 5000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(iotTelemetryLogRepository).findByCompanyIdOrderByCreatedAtDescIdDesc(eq(1L), captor.capture());
        assertEquals(100, captor.getValue().getPageSize());
    }
}
