package com.grash.service;

import com.grash.dto.integration.IotIntegrationPatchDTO;
import com.grash.dto.integration.IotTokenDTO;
import com.grash.dto.integration.MapsIntegrationPatchDTO;
import com.grash.dto.integration.NotificationsIntegrationPatchDTO;
import com.grash.dto.integration.PaymentsIntegrationPatchDTO;
import com.grash.exception.CustomException;
import com.grash.model.Company;
import com.grash.model.IntegrationConfig;
import com.grash.model.enums.MapsProvider;
import com.grash.model.enums.PaymentEnvironment;
import com.grash.model.enums.PaymentProvider;
import com.grash.model.enums.WhatsappProvider;
import com.grash.repository.IntegrationConfigRepository;
import com.grash.repository.IotTelemetryLogRepository;
import com.grash.utils.SecretCipher;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationServiceTest {

    @Mock
    private IntegrationConfigRepository integrationConfigRepository;
    @Mock
    private IotTelemetryLogRepository iotTelemetryLogRepository;
    @Mock
    private SecretCipher secretCipher;
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

    // ------------------------------------------------------------------ maps

    @Test
    void updateMaps_nonEmptyKeyIsEncryptedAndOnlyLast4Kept() {
        when(secretCipher.encrypt("AIza-secret-key-1234")).thenReturn("v1:encrypted");
        MapsIntegrationPatchDTO patch = new MapsIntegrationPatchDTO();
        patch.setProvider(MapsProvider.GOOGLE_MAPS);
        patch.setApiKey("AIza-secret-key-1234");
        patch.setEnabled(true);

        IntegrationConfig updated = service.updateMaps(company, patch);

        assertEquals("v1:encrypted", updated.getMapsApiKeyEnc());
        assertEquals("1234", updated.getMapsApiKeyLast4());
        assertTrue(updated.isMapsEnabled());
        assertEquals(MapsProvider.GOOGLE_MAPS, updated.getMapsProvider());
    }

    @Test
    void updateMaps_nullKeyKeepsExistingKey() {
        config.setMapsApiKeyEnc("v1:old");
        config.setMapsApiKeyLast4("9999");
        MapsIntegrationPatchDTO patch = new MapsIntegrationPatchDTO();
        patch.setSearchRadiusKm(10.0);

        IntegrationConfig updated = service.updateMaps(company, patch);

        assertEquals("v1:old", updated.getMapsApiKeyEnc());
        assertEquals("9999", updated.getMapsApiKeyLast4());
        assertEquals(10.0, updated.getSearchRadiusKm());
        verify(secretCipher, never()).encrypt(any());
    }

    @Test
    void updateMaps_emptyStringDeletesKeyWithoutNeedingEncryptionKey() {
        config.setMapsApiKeyEnc("v1:old");
        config.setMapsApiKeyLast4("9999");
        MapsIntegrationPatchDTO patch = new MapsIntegrationPatchDTO();
        patch.setApiKey("");

        IntegrationConfig updated = service.updateMaps(company, patch);

        assertNull(updated.getMapsApiKeyEnc());
        assertNull(updated.getMapsApiKeyLast4());
        verify(secretCipher, never()).encrypt(any());
    }

    @Test
    void updateMaps_enableWithoutApiKey_400() {
        MapsIntegrationPatchDTO patch = new MapsIntegrationPatchDTO();
        patch.setProvider(MapsProvider.MAPBOX);
        patch.setEnabled(true);

        CustomException ex = assertThrows(CustomException.class, () -> service.updateMaps(company, patch));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertFalse(config.isMapsEnabled());
    }

    @Test
    void updateMaps_deletingKeyWhileEnabled_400() {
        config.setMapsEnabled(true);
        config.setMapsProvider(MapsProvider.MAPBOX);
        config.setMapsApiKeyEnc("v1:old");
        MapsIntegrationPatchDTO patch = new MapsIntegrationPatchDTO();
        patch.setApiKey("");

        CustomException ex = assertThrows(CustomException.class, () -> service.updateMaps(company, patch));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertEquals("v1:old", config.getMapsApiKeyEnc());
    }

    @Test
    void updateMaps_invalidCoordinatesAndRadius_400() {
        MapsIntegrationPatchDTO lat = new MapsIntegrationPatchDTO();
        lat.setDefaultCenterLat(90.5);
        MapsIntegrationPatchDTO lng = new MapsIntegrationPatchDTO();
        lng.setDefaultCenterLng(-180.1);
        MapsIntegrationPatchDTO radiusZero = new MapsIntegrationPatchDTO();
        radiusZero.setSearchRadiusKm(0.0);
        MapsIntegrationPatchDTO radiusTooBig = new MapsIntegrationPatchDTO();
        radiusTooBig.setSearchRadiusKm(500.1);

        for (MapsIntegrationPatchDTO patch : List.of(lat, lng, radiusZero, radiusTooBig)) {
            CustomException ex = assertThrows(CustomException.class, () -> service.updateMaps(company, patch));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        }
    }

    @Test
    void updateMaps_boundaryValuesAccepted_andExplicitNullClears() {
        MapsIntegrationPatchDTO patch = new MapsIntegrationPatchDTO();
        patch.setDefaultCenterLat(-90.0);
        patch.setDefaultCenterLng(180.0);
        patch.setSearchRadiusKm(500.0);
        IntegrationConfig updated = service.updateMaps(company, patch);
        assertEquals(-90.0, updated.getDefaultCenterLat());
        assertEquals(180.0, updated.getDefaultCenterLng());
        assertEquals(500.0, updated.getSearchRadiusKm());

        MapsIntegrationPatchDTO clear = new MapsIntegrationPatchDTO();
        clear.setSearchRadiusKm(null);
        updated = service.updateMaps(company, clear);
        assertNull(updated.getSearchRadiusKm());
        assertEquals(-90.0, updated.getDefaultCenterLat());
    }

    @Test
    void updateMaps_encryptionKeyMissing_failsAndStoresNothing() {
        when(secretCipher.encrypt(any())).thenThrow(
                new CustomException(SecretCipher.KEY_MISSING_MESSAGE, HttpStatus.CONFLICT));
        MapsIntegrationPatchDTO patch = new MapsIntegrationPatchDTO();
        patch.setApiKey("some-key");

        CustomException ex = assertThrows(CustomException.class, () -> service.updateMaps(company, patch));

        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        assertEquals("INTEGRATIONS_ENCRYPTION_KEY no configurada", ex.getMessage());
        assertNull(config.getMapsApiKeyEnc());
        assertNull(config.getMapsApiKeyLast4());
        verify(integrationConfigRepository, never()).save(any(IntegrationConfig.class));
    }

    // -------------------------------------------------------------- payments

    @Test
    void updatePayments_secretsEncryptedWithLast4_publicKeyPlain() {
        when(secretCipher.encrypt("sk_test_abcdefWXYZ")).thenReturn("v1:sk");
        when(secretCipher.encrypt("whsec_12345678QRST")).thenReturn("v1:wh");
        PaymentsIntegrationPatchDTO patch = new PaymentsIntegrationPatchDTO();
        patch.setProvider(PaymentProvider.STRIPE);
        patch.setEnvironment(PaymentEnvironment.SANDBOX);
        patch.setPublicKey("pk_test_public");
        patch.setSecretKey("sk_test_abcdefWXYZ");
        patch.setWebhookSecret("whsec_12345678QRST");
        patch.setPlatformCommissionPercent(2.5);
        patch.setEnabled(true);

        IntegrationConfig updated = service.updatePayments(company, patch);

        assertEquals("v1:sk", updated.getPaymentSecretKeyEnc());
        assertEquals("WXYZ", updated.getPaymentSecretKeyLast4());
        assertEquals("v1:wh", updated.getPaymentWebhookSecretEnc());
        assertEquals("QRST", updated.getPaymentWebhookSecretLast4());
        assertEquals("pk_test_public", updated.getPaymentPublicKey());
        assertEquals(2.5, updated.getPlatformCommissionPercent());
        assertTrue(updated.isPaymentsEnabled());
    }

    @Test
    void updatePayments_nullKeepsAndEmptyDeletesSecrets() {
        config.setPaymentSecretKeyEnc("v1:sk");
        config.setPaymentSecretKeyLast4("WXYZ");
        config.setPaymentWebhookSecretEnc("v1:wh");
        config.setPaymentWebhookSecretLast4("QRST");
        PaymentsIntegrationPatchDTO patch = new PaymentsIntegrationPatchDTO();
        patch.setWebhookSecret("");

        IntegrationConfig updated = service.updatePayments(company, patch);

        assertEquals("v1:sk", updated.getPaymentSecretKeyEnc());
        assertEquals("WXYZ", updated.getPaymentSecretKeyLast4());
        assertNull(updated.getPaymentWebhookSecretEnc());
        assertNull(updated.getPaymentWebhookSecretLast4());
    }

    @Test
    void updatePayments_enableWithoutSecretKey_400() {
        PaymentsIntegrationPatchDTO patch = new PaymentsIntegrationPatchDTO();
        patch.setProvider(PaymentProvider.STRIPE);
        patch.setEnabled(true);

        CustomException ex = assertThrows(CustomException.class, () -> service.updatePayments(company, patch));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void updatePayments_commissionOutOfRange_400() {
        for (double value : new double[]{-0.1, 100.1}) {
            PaymentsIntegrationPatchDTO patch = new PaymentsIntegrationPatchDTO();
            patch.setPlatformCommissionPercent(value);
            CustomException ex = assertThrows(CustomException.class, () -> service.updatePayments(company, patch));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        }
        PaymentsIntegrationPatchDTO ok = new PaymentsIntegrationPatchDTO();
        ok.setPlatformCommissionPercent(100.0);
        assertEquals(100.0, service.updatePayments(company, ok).getPlatformCommissionPercent());
    }

    @Test
    void updatePayments_encryptionKeyMissing_failsAndStoresNothing() {
        when(secretCipher.encrypt(any())).thenThrow(
                new CustomException(SecretCipher.KEY_MISSING_MESSAGE, HttpStatus.CONFLICT));
        PaymentsIntegrationPatchDTO patch = new PaymentsIntegrationPatchDTO();
        patch.setSecretKey("sk_test_abc");
        patch.setPublicKey("pk_test_abc");

        CustomException ex = assertThrows(CustomException.class, () -> service.updatePayments(company, patch));

        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        assertNull(config.getPaymentSecretKeyEnc());
        assertNull(config.getPaymentPublicKey());
    }

    // --------------------------------------------------------- notifications

    @Test
    void updateNotifications_tokenEncryptedAndFlagsUpdated() {
        when(secretCipher.encrypt("EAAG-token-ABCD")).thenReturn("v1:tok");
        NotificationsIntegrationPatchDTO patch = new NotificationsIntegrationPatchDTO();
        patch.setProvider(WhatsappProvider.META_CLOUD_API);
        patch.setPhoneNumberId("123456789");
        patch.setAccessToken("EAAG-token-ABCD");
        patch.setWhatsappEnabled(true);
        patch.setNotifyOnWorkOrderCreated(true);
        patch.setNotifyOnTechnicianAssigned(true);

        IntegrationConfig updated = service.updateNotifications(company, patch);

        assertEquals("v1:tok", updated.getWhatsappAccessTokenEnc());
        assertEquals("ABCD", updated.getWhatsappAccessTokenLast4());
        assertEquals("123456789", updated.getWhatsappPhoneNumberId());
        assertTrue(updated.isWhatsappEnabled());
        assertTrue(updated.isNotifyOnWorkOrderCreated());
        assertTrue(updated.isNotifyOnTechnicianAssigned());
        assertFalse(updated.isNotifyOnTechnicianArrived());
        assertFalse(updated.isNotifyOnWorkOrderCompleted());
    }

    @Test
    void updateNotifications_enableWithoutPhoneNumberIdOrToken_400() {
        NotificationsIntegrationPatchDTO noToken = new NotificationsIntegrationPatchDTO();
        noToken.setProvider(WhatsappProvider.TWILIO);
        noToken.setPhoneNumberId("+14155238886");
        noToken.setWhatsappEnabled(true);
        NotificationsIntegrationPatchDTO noPhone = new NotificationsIntegrationPatchDTO();
        noPhone.setProvider(WhatsappProvider.TWILIO);
        noPhone.setAccessToken("token");
        noPhone.setWhatsappEnabled(true);

        for (NotificationsIntegrationPatchDTO patch : List.of(noToken, noPhone)) {
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.updateNotifications(company, patch));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        }
        assertFalse(config.isWhatsappEnabled());
    }

    @Test
    void updateNotifications_emptyTokenDeletesAndNullKeeps() {
        config.setWhatsappAccessTokenEnc("v1:tok");
        config.setWhatsappAccessTokenLast4("ABCD");
        NotificationsIntegrationPatchDTO keep = new NotificationsIntegrationPatchDTO();
        keep.setNotifyOnWorkOrderCompleted(true);
        service.updateNotifications(company, keep);
        assertEquals("v1:tok", config.getWhatsappAccessTokenEnc());
        assertTrue(config.isNotifyOnWorkOrderCompleted());

        NotificationsIntegrationPatchDTO delete = new NotificationsIntegrationPatchDTO();
        delete.setAccessToken("");
        service.updateNotifications(company, delete);
        assertNull(config.getWhatsappAccessTokenEnc());
        assertNull(config.getWhatsappAccessTokenLast4());
    }
}
