package com.grash.controller;

import com.grash.dto.integration.IntegrationTestResultDTO;
import com.grash.dto.integration.IotIntegrationPatchDTO;
import com.grash.dto.integration.IotIntegrationShowDTO;
import com.grash.dto.integration.IotTelemetryLogShowDTO;
import com.grash.dto.integration.IotTokenDTO;
import com.grash.dto.integration.MapsIntegrationPatchDTO;
import com.grash.dto.integration.MapsIntegrationShowDTO;
import com.grash.dto.integration.NotificationsIntegrationPatchDTO;
import com.grash.dto.integration.NotificationsIntegrationShowDTO;
import com.grash.dto.integration.PaymentsIntegrationPatchDTO;
import com.grash.dto.integration.PaymentsIntegrationShowDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.IntegrationMapper;
import com.grash.model.Company;
import com.grash.model.IntegrationConfig;
import com.grash.model.IotTelemetryLog;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.enums.IotTelemetryStatus;
import com.grash.model.enums.RoleCode;
import com.grash.model.enums.MapsProvider;
import com.grash.model.enums.PaymentEnvironment;
import com.grash.model.enums.PaymentProvider;
import com.grash.model.enums.RoleType;
import com.grash.model.enums.WhatsappProvider;
import com.grash.service.IntegrationService;
import com.grash.service.IntegrationTestService;
import com.grash.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import static com.grash.utils.Helper.setCurrentUser;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IntegrationController.class)
@AutoConfigureMockMvc(addFilters = false)
class IntegrationControllerTest extends AbstractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IntegrationService integrationService;
    @MockitoBean
    private IntegrationMapper integrationMapper;
    @MockitoBean
    private IntegrationTestService integrationTestService;
    @MockitoBean
    private UserService userService;

    private User admin;
    private User technician;
    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);

        admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@test.com");
        admin.setEnabled(true);
        admin.setCompany(company);
        admin.setRole(Role.builder().id(1L).roleType(RoleType.ROLE_CLIENT).code(RoleCode.ADMIN).name("Admin")
                .viewPermissions(new HashSet<>()).build());

        technician = new User();
        technician.setId(2L);
        technician.setEmail("tech@test.com");
        technician.setEnabled(true);
        technician.setCompany(company);
        technician.setRole(Role.builder().id(2L).roleType(RoleType.ROLE_CLIENT).code(RoleCode.TECHNICIAN)
                .name("Tech").viewPermissions(new HashSet<>()).build());
    }

    private IotIntegrationShowDTO iotDto(boolean hasToken) {
        IotIntegrationShowDTO dto = new IotIntegrationShowDTO();
        dto.setEnabled(true);
        dto.setHasToken(hasToken);
        dto.setTokenLast4(hasToken ? "abcd" : null);
        return dto;
    }

    @Test
    void get_nonAdmin_returns403() throws Exception {
        setCurrentUser(technician);
        when(userService.whoami(any())).thenReturn(technician);

        mockMvc.perform(get("/integrations")).andExpect(status().isForbidden());

        verifyNoInteractions(integrationService);
    }

    @Test
    void get_admin_returnsMaskedIotStatusWithIngestUrl() throws Exception {
        setCurrentUser(admin);
        when(userService.whoami(any())).thenReturn(admin);
        IntegrationConfig config = new IntegrationConfig();
        config.setIotTokenHash("secret-hash");
        when(integrationService.getOrCreate(company)).thenReturn(config);
        when(integrationMapper.toIotShowDto(config)).thenReturn(iotDto(true));

        mockMvc.perform(get("/integrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iot.enabled").value(true))
                .andExpect(jsonPath("$.iot.hasToken").value(true))
                .andExpect(jsonPath("$.iot.tokenLast4").value("abcd"))
                .andExpect(jsonPath("$.iot.ingestUrl", org.hamcrest.Matchers.endsWith("/public/iot/telemetry")))
                .andExpect(jsonPath("$.iot.token").doesNotExist())
                .andExpect(jsonPath("$.iot.iotTokenHash").doesNotExist());
    }

    @Test
    void patchIot_nonAdmin_returns403() throws Exception {
        setCurrentUser(technician);
        when(userService.whoami(any())).thenReturn(technician);

        mockMvc.perform(patch("/integrations/iot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(integrationService);
    }

    @Test
    void patchIot_admin_passesBodyToService() throws Exception {
        setCurrentUser(admin);
        when(userService.whoami(any())).thenReturn(admin);
        IntegrationConfig config = new IntegrationConfig();
        when(integrationService.updateIot(eq(company), any(IotIntegrationPatchDTO.class))).thenReturn(config);
        when(integrationMapper.toIotShowDto(config)).thenReturn(iotDto(false));

        mockMvc.perform(patch("/integrations/iot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"webhookAlertUrl\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.hasToken").value(false));

        ArgumentCaptor<IotIntegrationPatchDTO> captor = ArgumentCaptor.forClass(IotIntegrationPatchDTO.class);
        verify(integrationService).updateIot(eq(company), captor.capture());
        assertTrue(captor.getValue().getEnabled());
        assertTrue(captor.getValue().isWebhookAlertUrlSet());
    }

    @Test
    void patchIot_invalidUrl_returns400() throws Exception {
        setCurrentUser(admin);
        when(userService.whoami(any())).thenReturn(admin);
        when(integrationService.updateIot(eq(company), any(IotIntegrationPatchDTO.class)))
                .thenThrow(new CustomException("bad url", HttpStatus.BAD_REQUEST));

        mockMvc.perform(patch("/integrations/iot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhookAlertUrl\":\"http://localhost\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rotateToken_nonAdmin_returns403() throws Exception {
        setCurrentUser(technician);
        when(userService.whoami(any())).thenReturn(technician);

        mockMvc.perform(post("/integrations/iot/token")).andExpect(status().isForbidden());

        verify(integrationService, never()).rotateIotToken(any());
    }

    @Test
    void rotateToken_admin_returnsClearTokenOnce() throws Exception {
        setCurrentUser(admin);
        when(userService.whoami(any())).thenReturn(admin);
        when(integrationService.rotateIotToken(company)).thenReturn(new IotTokenDTO("iot_live_abcdefgh", "efgh"));

        mockMvc.perform(post("/integrations/iot/token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("iot_live_abcdefgh"))
                .andExpect(jsonPath("$.tokenLast4").value("efgh"));

        // the subsequent GET never exposes the token
        IntegrationConfig config = new IntegrationConfig();
        when(integrationService.getOrCreate(company)).thenReturn(config);
        when(integrationMapper.toIotShowDto(config)).thenReturn(iotDto(true));
        mockMvc.perform(get("/integrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iot.token").doesNotExist());
    }

    @Test
    void telemetry_nonAdmin_returns403() throws Exception {
        setCurrentUser(technician);
        when(userService.whoami(any())).thenReturn(technician);

        mockMvc.perform(get("/integrations/iot/telemetry")).andExpect(status().isForbidden());
    }

    @Test
    void telemetry_admin_returnsMappedList() throws Exception {
        setCurrentUser(admin);
        when(userService.whoami(any())).thenReturn(admin);
        IotTelemetryLog entity = new IotTelemetryLog();
        when(integrationService.getLatestTelemetry(1L, 5)).thenReturn(List.of(entity));
        IotTelemetryLogShowDTO dto = new IotTelemetryLogShowDTO();
        dto.setId(7L);
        dto.setAssetCode("AST-1");
        dto.setMetricName("temp");
        dto.setValue(42.5);
        dto.setStatus(IotTelemetryStatus.TRIGGERED);
        dto.setTimestamp(Instant.parse("2026-01-01T00:00:00Z"));
        when(integrationMapper.toTelemetryShowDto(entity)).thenReturn(dto);

        mockMvc.perform(get("/integrations/iot/telemetry?size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].assetCode").value("AST-1"))
                .andExpect(jsonPath("$[0].status").value("TRIGGERED"));
    }

    // ---------------------------------------------------- maps / payments / notifications

    private MapsIntegrationShowDTO mapsDto() {
        MapsIntegrationShowDTO dto = new MapsIntegrationShowDTO();
        dto.setEnabled(true);
        dto.setProvider(MapsProvider.GOOGLE_MAPS);
        dto.setHasApiKey(true);
        dto.setApiKeyLast4("1234");
        dto.setDefaultCenterLat(18.48);
        dto.setDefaultCenterLng(-69.93);
        dto.setSearchRadiusKm(25.0);
        return dto;
    }

    private PaymentsIntegrationShowDTO paymentsDto() {
        PaymentsIntegrationShowDTO dto = new PaymentsIntegrationShowDTO();
        dto.setEnabled(true);
        dto.setProvider(PaymentProvider.STRIPE);
        dto.setEnvironment(PaymentEnvironment.SANDBOX);
        dto.setPublicKey("pk_test_public");
        dto.setHasSecretKey(true);
        dto.setSecretKeyLast4("WXYZ");
        dto.setHasWebhookSecret(false);
        dto.setPlatformCommissionPercent(2.5);
        return dto;
    }

    private NotificationsIntegrationShowDTO notificationsDto() {
        NotificationsIntegrationShowDTO dto = new NotificationsIntegrationShowDTO();
        dto.setWhatsappEnabled(true);
        dto.setProvider(WhatsappProvider.META_CLOUD_API);
        dto.setPhoneNumberId("123456789");
        dto.setHasAccessToken(true);
        dto.setAccessTokenLast4("ABCD");
        dto.setNotifyOnWorkOrderCreated(true);
        return dto;
    }

    @Test
    void get_admin_returnsMaskedMapsPaymentsAndNotifications() throws Exception {
        setCurrentUser(admin);
        when(userService.whoami(any())).thenReturn(admin);
        IntegrationConfig config = new IntegrationConfig();
        config.setMapsApiKeyEnc("v1:secret-ciphertext");
        config.setPaymentSecretKeyEnc("v1:secret-ciphertext");
        config.setWhatsappAccessTokenEnc("v1:secret-ciphertext");
        when(integrationService.getOrCreate(company)).thenReturn(config);
        when(integrationMapper.toIotShowDto(config)).thenReturn(iotDto(false));
        when(integrationMapper.toMapsShowDto(config)).thenReturn(mapsDto());
        when(integrationMapper.toPaymentsShowDto(config)).thenReturn(paymentsDto());
        when(integrationMapper.toNotificationsShowDto(config)).thenReturn(notificationsDto());

        mockMvc.perform(get("/integrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iot.enabled").value(true))
                .andExpect(jsonPath("$.maps.enabled").value(true))
                .andExpect(jsonPath("$.maps.provider").value("GOOGLE_MAPS"))
                .andExpect(jsonPath("$.maps.hasApiKey").value(true))
                .andExpect(jsonPath("$.maps.apiKeyLast4").value("1234"))
                .andExpect(jsonPath("$.maps.defaultCenterLat").value(18.48))
                .andExpect(jsonPath("$.maps.apiKey").doesNotExist())
                .andExpect(jsonPath("$.maps.mapsApiKeyEnc").doesNotExist())
                .andExpect(jsonPath("$.payments.environment").value("SANDBOX"))
                .andExpect(jsonPath("$.payments.publicKey").value("pk_test_public"))
                .andExpect(jsonPath("$.payments.hasSecretKey").value(true))
                .andExpect(jsonPath("$.payments.secretKeyLast4").value("WXYZ"))
                .andExpect(jsonPath("$.payments.hasWebhookSecret").value(false))
                .andExpect(jsonPath("$.payments.webhookSecretLast4").doesNotExist())
                .andExpect(jsonPath("$.payments.secretKey").doesNotExist())
                .andExpect(jsonPath("$.payments.paymentSecretKeyEnc").doesNotExist())
                .andExpect(jsonPath("$.notifications.whatsappEnabled").value(true))
                .andExpect(jsonPath("$.notifications.provider").value("META_CLOUD_API"))
                .andExpect(jsonPath("$.notifications.phoneNumberId").value("123456789"))
                .andExpect(jsonPath("$.notifications.hasAccessToken").value(true))
                .andExpect(jsonPath("$.notifications.accessTokenLast4").value("ABCD"))
                .andExpect(jsonPath("$.notifications.notifyOnWorkOrderCreated").value(true))
                .andExpect(jsonPath("$.notifications.notifyOnTechnicianArrived").value(false))
                .andExpect(jsonPath("$.notifications.accessToken").doesNotExist())
                .andExpect(jsonPath("$.notifications.whatsappAccessTokenEnc").doesNotExist());
    }

    @Test
    void newEndpoints_nonAdmin_return403AndNeverTouchServices() throws Exception {
        setCurrentUser(technician);
        when(userService.whoami(any())).thenReturn(technician);

        mockMvc.perform(patch("/integrations/maps").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/integrations/maps/test")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/integrations/payments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/integrations/payments/test")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/integrations/notifications").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/integrations/notifications/test-whatsapp").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"+18095551234\"}")).andExpect(status().isForbidden());

        verifyNoInteractions(integrationService);
        verifyNoInteractions(integrationTestService);
    }

    @Test
    void patchMaps_admin_passesBodyAndReturnsMaskedSection() throws Exception {
        setCurrentUser(admin);
        when(userService.whoami(any())).thenReturn(admin);
        IntegrationConfig config = new IntegrationConfig();
        when(integrationService.updateMaps(eq(company), any(MapsIntegrationPatchDTO.class))).thenReturn(config);
        when(integrationMapper.toMapsShowDto(config)).thenReturn(mapsDto());

        mockMvc.perform(patch("/integrations/maps").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"provider\":\"GOOGLE_MAPS\",\"apiKey\":\"AIza-secret\","
                                + "\"defaultCenterLat\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasApiKey").value(true))
                .andExpect(jsonPath("$.apiKeyLast4").value("1234"))
                .andExpect(jsonPath("$.apiKey").doesNotExist());

        ArgumentCaptor<MapsIntegrationPatchDTO> captor = ArgumentCaptor.forClass(MapsIntegrationPatchDTO.class);
        verify(integrationService).updateMaps(eq(company), captor.capture());
        assertTrue(captor.getValue().getEnabled());
        assertTrue(captor.getValue().isDefaultCenterLatSet());
        assertTrue(!captor.getValue().isDefaultCenterLngSet());
        assertTrue("AIza-secret".equals(captor.getValue().getApiKey()));
    }

    @Test
    void patchMaps_serviceValidationError_returns400() throws Exception {
        setCurrentUser(admin);
        when(userService.whoami(any())).thenReturn(admin);
        when(integrationService.updateMaps(eq(company), any(MapsIntegrationPatchDTO.class)))
                .thenThrow(new CustomException("defaultCenterLat must be between -90.0 and 90.0",
                        HttpStatus.BAD_REQUEST));

        mockMvc.perform(patch("/integrations/maps").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultCenterLat\":123}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchMaps_missingEncryptionKey_returns409() throws Exception {
        setCurrentUser(admin);
        when(userService.whoami(any())).thenReturn(admin);
        when(integrationService.updateMaps(eq(company), any(MapsIntegrationPatchDTO.class)))
                .thenThrow(new CustomException("INTEGRATIONS_ENCRYPTION_KEY no configurada", HttpStatus.CONFLICT));

        mockMvc.perform(patch("/integrations/maps").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"abc\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void patchPayments_admin_passesBodyAndReturnsMaskedSection() throws Exception {
        setCurrentUser(admin);
        when(userService.whoami(any())).thenReturn(admin);
        IntegrationConfig config = new IntegrationConfig();
        when(integrationService.updatePayments(eq(company), any(PaymentsIntegrationPatchDTO.class)))
                .thenReturn(config);
        when(integrationMapper.toPaymentsShowDto(config)).thenReturn(paymentsDto());

        mockMvc.perform(patch("/integrations/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"STRIPE\",\"environment\":\"SANDBOX\",\"secretKey\":\"\","
                                + "\"webhookSecret\":\"whsec_x\",\"platformCommissionPercent\":2.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasSecretKey").value(true))
                .andExpect(jsonPath("$.secretKeyLast4").value("WXYZ"))
                .andExpect(jsonPath("$.secretKey").doesNotExist())
                .andExpect(jsonPath("$.webhookSecret").doesNotExist());

        ArgumentCaptor<PaymentsIntegrationPatchDTO> captor =
                ArgumentCaptor.forClass(PaymentsIntegrationPatchDTO.class);
        verify(integrationService).updatePayments(eq(company), captor.capture());
        assertTrue("".equals(captor.getValue().getSecretKey()));
        assertTrue("whsec_x".equals(captor.getValue().getWebhookSecret()));
        assertTrue(captor.getValue().isPlatformCommissionPercentSet());
    }

    @Test
    void patchNotifications_admin_passesBodyAndReturnsMaskedSection() throws Exception {
        setCurrentUser(admin);
        when(userService.whoami(any())).thenReturn(admin);
        IntegrationConfig config = new IntegrationConfig();
        when(integrationService.updateNotifications(eq(company), any(NotificationsIntegrationPatchDTO.class)))
                .thenReturn(config);
        when(integrationMapper.toNotificationsShowDto(config)).thenReturn(notificationsDto());

        mockMvc.perform(patch("/integrations/notifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"whatsappEnabled\":true,\"provider\":\"META_CLOUD_API\","
                                + "\"accessToken\":\"EAAG\",\"notifyOnWorkOrderCreated\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAccessToken").value(true))
                .andExpect(jsonPath("$.accessTokenLast4").value("ABCD"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        ArgumentCaptor<NotificationsIntegrationPatchDTO> captor =
                ArgumentCaptor.forClass(NotificationsIntegrationPatchDTO.class);
        verify(integrationService).updateNotifications(eq(company), captor.capture());
        assertTrue(captor.getValue().getWhatsappEnabled());
        assertTrue(captor.getValue().getNotifyOnWorkOrderCreated());
        assertTrue("EAAG".equals(captor.getValue().getAccessToken()));
    }

    @Test
    void testEndpoints_admin_returnResultOfStoredCredentialsTest() throws Exception {
        setCurrentUser(admin);
        when(userService.whoami(any())).thenReturn(admin);
        IntegrationConfig config = new IntegrationConfig();
        when(integrationService.getOrCreate(company)).thenReturn(config);
        when(integrationTestService.testMaps(config)).thenReturn(new IntegrationTestResultDTO(true, "maps ok"));
        when(integrationTestService.testPayments(config))
                .thenReturn(new IntegrationTestResultDTO(false, "payments bad"));

        mockMvc.perform(post("/integrations/maps/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("maps ok"));
        mockMvc.perform(post("/integrations/payments/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("payments bad"));
    }

    @Test
    void testWhatsapp_admin_validPhone_callsClientWithStoredConfig() throws Exception {
        setCurrentUser(admin);
        when(userService.whoami(any())).thenReturn(admin);
        IntegrationConfig config = new IntegrationConfig();
        when(integrationService.getOrCreate(company)).thenReturn(config);
        when(integrationTestService.testWhatsapp(config, "+18095551234"))
                .thenReturn(new IntegrationTestResultDTO(true, "sent"));

        mockMvc.perform(post("/integrations/notifications/test-whatsapp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+18095551234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("sent"));
    }

    @Test
    void testWhatsapp_invalidPhone_returns400WithoutSending() throws Exception {
        setCurrentUser(admin);
        when(userService.whoami(any())).thenReturn(admin);

        for (String phone : new String[]{"8095551234", "+0123456789", "+123", "abc", ""}) {
            mockMvc.perform(post("/integrations/notifications/test-whatsapp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"" + phone + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/integrations/notifications/test-whatsapp").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());

        verifyNoInteractions(integrationTestService);
    }
}
