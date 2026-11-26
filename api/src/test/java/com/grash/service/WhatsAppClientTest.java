package com.grash.service;

import com.grash.dto.integration.IntegrationTestResultDTO;
import com.grash.model.IntegrationConfig;
import com.grash.model.enums.WhatsappProvider;
import com.grash.utils.SecretCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppClientTest {

    private static final String TOKEN = "EAAG-TOKEN-secret-9876";
    private static final String SID = "AC" + "0".repeat(32);

    @Mock
    private SecretCipher secretCipher;
    @Mock
    private RestTemplate restTemplate;

    private WhatsAppClient client;
    private IntegrationConfig config;

    @BeforeEach
    void setUp() {
        client = new WhatsAppClient(secretCipher);
        client.setRestTemplate(restTemplate);
        config = new IntegrationConfig();
        config.setWhatsappAccessTokenEnc("v1:enc");
        lenient().when(secretCipher.decrypt("v1:enc")).thenReturn(TOKEN);
    }

    private void respond(int status, String body) {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.status(status).body(body));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private HttpEntity capture(URI[] uriHolder) {
        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(uriCaptor.capture(), eq(HttpMethod.POST), entityCaptor.capture(),
                eq(String.class));
        uriHolder[0] = uriCaptor.getValue();
        return entityCaptor.getValue();
    }

    @Test
    void meta_success_sendsExpectedRequest() {
        config.setWhatsappProvider(WhatsappProvider.META_CLOUD_API);
        config.setWhatsappPhoneNumberId("123456789012");
        respond(200, "{\"messages\":[{\"id\":\"wamid\"}]}");

        IntegrationTestResultDTO result = client.send(config, "+18095551234", WhatsAppClient.TEST_MESSAGE);

        assertTrue(result.isSuccess());
        URI[] uri = new URI[1];
        HttpEntity<?> entity = capture(uri);
        assertEquals("graph.facebook.com", uri[0].getHost());
        assertEquals("/v21.0/123456789012/messages", uri[0].getPath());
        assertEquals("Bearer " + TOKEN, entity.getHeaders().getFirst("Authorization"));
        Map<?, ?> body = (Map<?, ?>) entity.getBody();
        assertEquals("whatsapp", body.get("messaging_product"));
        assertEquals("18095551234", body.get("to"));
        assertEquals("text", body.get("type"));
        assertEquals("Mensaje de prueba de Atlas CMMS", ((Map<?, ?>) body.get("text")).get("body"));
    }

    @Test
    void meta_error_returnsProviderMessageWithoutToken() {
        config.setWhatsappProvider(WhatsappProvider.META_CLOUD_API);
        config.setWhatsappPhoneNumberId("123456789012");
        respond(401, "{\"error\":{\"message\":\"Invalid OAuth access token " + TOKEN + "\"}}");

        IntegrationTestResultDTO result = client.send(config, "+18095551234", "hi");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("401"));
        assertFalse(result.getMessage().contains(TOKEN));
    }

    @Test
    void meta_nonNumericPhoneNumberId_isRejectedBeforeAnyCall() {
        config.setWhatsappProvider(WhatsappProvider.META_CLOUD_API);
        config.setWhatsappPhoneNumberId("../evil");

        IntegrationTestResultDTO result = client.send(config, "+18095551234", "hi");

        assertFalse(result.isSuccess());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void twilio_success_sendsFormWithBasicAuth() {
        config.setWhatsappProvider(WhatsappProvider.TWILIO);
        config.setWhatsappAccountSid(SID);
        config.setWhatsappPhoneNumberId("+14155238886");
        respond(201, "{\"sid\":\"SM1\"}");

        IntegrationTestResultDTO result = client.send(config, "+18095551234", "hola");

        assertTrue(result.isSuccess());
        URI[] uri = new URI[1];
        HttpEntity<?> entity = capture(uri);
        assertEquals("api.twilio.com", uri[0].getHost());
        assertEquals("/2010-04-01/Accounts/" + SID + "/Messages.json", uri[0].getPath());
        assertTrue(entity.getHeaders().getFirst("Authorization").startsWith("Basic "));
        MultiValueMap<?, ?> form = (MultiValueMap<?, ?>) entity.getBody();
        assertEquals("whatsapp:+14155238886", form.getFirst("From"));
        assertEquals("whatsapp:+18095551234", form.getFirst("To"));
        assertEquals("hola", form.getFirst("Body"));
    }

    @Test
    void twilio_error_returnsProviderMessage() {
        config.setWhatsappProvider(WhatsappProvider.TWILIO);
        config.setWhatsappAccountSid(SID);
        config.setWhatsappPhoneNumberId("+14155238886");
        respond(400, "{\"message\":\"The To number is not a valid WhatsApp number\"}");

        IntegrationTestResultDTO result = client.send(config, "+18095551234", "hola");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("400"));
        assertTrue(result.getMessage().contains("not a valid WhatsApp number"));
    }

    @Test
    void twilio_invalidAccountSid_isRejectedBeforeAnyCall() {
        config.setWhatsappProvider(WhatsappProvider.TWILIO);
        config.setWhatsappAccountSid("../x");
        config.setWhatsappPhoneNumberId("+14155238886");

        assertFalse(client.send(config, "+18095551234", "hola").isSuccess());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void invalidPhone_isRejectedBeforeAnyCall() {
        config.setWhatsappProvider(WhatsappProvider.META_CLOUD_API);
        config.setWhatsappPhoneNumberId("123456789012");

        assertFalse(client.send(config, "8095551234", "hi").isSuccess());
        assertFalse(client.send(config, null, "hi").isSuccess());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void missingCredentials_failsWithoutCallingProvider() {
        IntegrationConfig empty = new IntegrationConfig();
        assertFalse(client.send(empty, "+18095551234", "hi").isSuccess());

        config.setWhatsappProvider(WhatsappProvider.META_CLOUD_API);
        assertFalse(client.send(config, "+18095551234", "hi").isSuccess());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void networkError_neverThrowsNorLeaksToken() {
        config.setWhatsappProvider(WhatsappProvider.META_CLOUD_API);
        config.setWhatsappPhoneNumberId("123456789012");
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("I/O error " + TOKEN));

        IntegrationTestResultDTO result = client.send(config, "+18095551234", "hi");

        assertFalse(result.isSuccess());
        assertFalse(result.getMessage().contains(TOKEN));
    }
}
