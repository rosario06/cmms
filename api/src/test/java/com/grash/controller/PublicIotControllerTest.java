package com.grash.controller;

import com.grash.dto.integration.IotTelemetryPostDTO;
import com.grash.exception.CustomException;
import com.grash.model.IotTelemetryLog;
import com.grash.model.enums.IotTelemetryStatus;
import com.grash.service.IotIngestService;
import com.grash.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicIotController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublicIotControllerTest extends AbstractControllerTest {

    private static final String VALID_BODY = "{\"assetCode\":\"AST-1\",\"metricName\":\"temp\",\"value\":42.5}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IotIngestService iotIngestService;
    @MockitoBean
    private UserService userService;

    private IotTelemetryLog logWithStatus(IotTelemetryStatus status, String message) {
        IotTelemetryLog entry = new IotTelemetryLog();
        entry.setStatus(status);
        entry.setMessage(message);
        return entry;
    }

    @Test
    void ingest_invalidToken_returns401() throws Exception {
        when(iotIngestService.ingest(eq("bad"), any(IotTelemetryPostDTO.class)))
                .thenThrow(new CustomException("Unauthorized", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post("/public/iot/telemetry").header("X-IoT-Token", "bad")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingest_missingToken_returns401() throws Exception {
        when(iotIngestService.ingest(isNull(), any(IotTelemetryPostDTO.class)))
                .thenThrow(new CustomException("Unauthorized", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post("/public/iot/telemetry")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingest_happyPath_returns200() throws Exception {
        when(iotIngestService.ingest(eq("iot_live_ok"), any(IotTelemetryPostDTO.class)))
                .thenReturn(logWithStatus(IotTelemetryStatus.TRIGGERED, "1 meter trigger(s) fired"));

        mockMvc.perform(post("/public/iot/telemetry").header("X-IoT-Token", "iot_live_ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetCode\":\"AST-1\",\"metricName\":\"temp\",\"value\":42.5," +
                                "\"timestamp\":\"2026-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIGGERED"));
    }

    @Test
    void ingest_assetNotFound_returns404() throws Exception {
        when(iotIngestService.ingest(eq("iot_live_ok"), any(IotTelemetryPostDTO.class)))
                .thenReturn(logWithStatus(IotTelemetryStatus.ASSET_NOT_FOUND, "Asset not found"));

        mockMvc.perform(post("/public/iot/telemetry").header("X-IoT-Token", "iot_live_ok")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("ASSET_NOT_FOUND"));
    }

    @Test
    void ingest_invalidBody_returns4xxWithoutCallingService() throws Exception {
        mockMvc.perform(post("/public/iot/telemetry").header("X-IoT-Token", "iot_live_ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetCode\":\"\",\"metricName\":\"temp\"}"))
                .andExpect(status().is4xxClientError());

        verify(iotIngestService, never()).ingest(any(), any());
    }
}
