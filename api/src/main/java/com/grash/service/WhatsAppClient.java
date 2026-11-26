package com.grash.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.grash.dto.integration.IntegrationTestResultDTO;
import com.grash.exception.CustomException;
import com.grash.model.IntegrationConfig;
import com.grash.model.enums.WhatsappProvider;
import com.grash.utils.IntegrationHttp;
import com.grash.utils.SecretCipher;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Single entry point to send a WhatsApp text message with the credentials stored in the IntegrationConfig.
 * Only calls fixed provider hosts. Never throws and never logs or returns secrets.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsAppClient {

    public static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{6,14}$");
    public static final String TEST_MESSAGE = "Mensaje de prueba de Atlas CMMS";
    private static final Pattern META_PHONE_NUMBER_ID = Pattern.compile("^\\d{5,32}$");
    private static final Pattern TWILIO_ACCOUNT_SID = Pattern.compile("^AC[0-9a-fA-F]{32}$");
    private static final String META_URL = "https://graph.facebook.com/v21.0/{phoneNumberId}/messages";
    private static final String TWILIO_URL = "https://api.twilio.com/2010-04-01/Accounts/{accountSid}/Messages.json";

    private final SecretCipher secretCipher;

    @Setter(AccessLevel.PACKAGE)
    private RestTemplate restTemplate = IntegrationHttp.buildRestTemplate();

    public IntegrationTestResultDTO send(IntegrationConfig config, String toE164, String body) {
        if (config.getWhatsappProvider() == null) {
            return fail("Selecciona un proveedor de WhatsApp.");
        }
        if (isBlank(config.getWhatsappPhoneNumberId()) || isBlank(config.getWhatsappAccessTokenEnc())) {
            return fail("Configura el identificador del número y el token de acceso antes de probar.");
        }
        if (toE164 == null || !E164.matcher(toE164).matches()) {
            return fail("El teléfono debe estar en formato E.164 (por ejemplo +18095551234).");
        }
        String token;
        try {
            token = secretCipher.decrypt(config.getWhatsappAccessTokenEnc());
        } catch (CustomException e) {
            return fail(e.getMessage());
        }
        try {
            return config.getWhatsappProvider() == WhatsappProvider.META_CLOUD_API
                    ? sendViaMeta(config, token, toE164, body)
                    : sendViaTwilio(config, token, toE164, body);
        } catch (RestClientException e) {
            // the exception message may contain the request URL: log only the type
            log.warn("WhatsApp request failed: {}", e.getClass().getSimpleName());
            return fail("No se pudo conectar con el proveedor (tiempo de espera o error de red).");
        } catch (RuntimeException e) {
            log.warn("WhatsApp request failed: {}", e.getClass().getSimpleName());
            return fail("Error inesperado al enviar el mensaje.");
        }
    }

    private IntegrationTestResultDTO sendViaMeta(IntegrationConfig config, String token, String toE164,
                                                 String body) {
        String phoneNumberId = config.getWhatsappPhoneNumberId();
        if (!META_PHONE_NUMBER_ID.matcher(phoneNumberId).matches()) {
            return fail("El identificador del número de Meta debe ser numérico.");
        }
        URI uri = UriComponentsBuilder.fromUriString(META_URL).encode().buildAndExpand(phoneNumberId).toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", toE164.substring(1));
        payload.put("type", "text");
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("body", body);
        payload.put("text", text);
        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST,
                new HttpEntity<>(payload, headers), String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            return new IntegrationTestResultDTO(true, "Mensaje enviado correctamente.");
        }
        JsonNode json = IntegrationHttp.parseJson(response.getBody());
        String detail = json != null ? json.path("error").path("message").asText("") : "";
        return fail(failureMessage(response.getStatusCode().value(), detail, token));
    }

    private IntegrationTestResultDTO sendViaTwilio(IntegrationConfig config, String token, String toE164,
                                                   String body) {
        String accountSid = config.getWhatsappAccountSid();
        if (accountSid == null || !TWILIO_ACCOUNT_SID.matcher(accountSid).matches()) {
            return fail("Falta un Account SID de Twilio válido.");
        }
        String from = config.getWhatsappPhoneNumberId();
        if (!from.startsWith("whatsapp:")) {
            from = "whatsapp:" + from;
        }
        URI uri = UriComponentsBuilder.fromUriString(TWILIO_URL).encode().buildAndExpand(accountSid).toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(accountSid, token);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", from);
        form.add("To", "whatsapp:" + toE164);
        form.add("Body", body);
        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST,
                new HttpEntity<>(form, headers), String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            return new IntegrationTestResultDTO(true, "Mensaje enviado correctamente.");
        }
        JsonNode json = IntegrationHttp.parseJson(response.getBody());
        String detail = json != null ? json.path("message").asText("") : "";
        return fail(failureMessage(response.getStatusCode().value(), detail, token));
    }

    private static String failureMessage(int status, String detail, String token) {
        String safe = IntegrationHttp.safeMessage(detail, token);
        return "El proveedor rechazó el envío (HTTP " + status + ")" + (safe.isEmpty() ? "." : ": " + safe);
    }

    private static IntegrationTestResultDTO fail(String message) {
        return new IntegrationTestResultDTO(false, message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
