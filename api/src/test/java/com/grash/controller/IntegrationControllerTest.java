package com.grash.controller;

import com.grash.dto.integration.IotIntegrationPatchDTO;
import com.grash.dto.integration.IotIntegrationShowDTO;
import com.grash.dto.integration.IotTelemetryLogShowDTO;
import com.grash.dto.integration.IotTokenDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.IntegrationMapper;
import com.grash.model.Company;
import com.grash.model.IntegrationConfig;
import com.grash.model.IotTelemetryLog;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.enums.IotTelemetryStatus;
import com.grash.model.enums.RoleCode;
import com.grash.model.enums.RoleType;
import com.grash.service.IntegrationService;
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
}
