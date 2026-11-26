package com.grash.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.grash.dto.integration.IntegrationTestResultDTO;
import com.grash.exception.CustomException;
import com.grash.model.IntegrationConfig;
import com.grash.model.enums.PaymentEnvironment;
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

/**
 * Connection tests of the integrations. They always use the credentials already stored (decrypted), never
 * values coming from the request, and only call fixed provider hosts. Errors never include secrets.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IntegrationTestService {

    static final String GOOGLE_GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json";
    static final String MAPBOX_GEOCODE_URL = "https://api.mapbox.com/geocoding/v5/mapbox.places/{query}.json";
    static final String STRIPE_BALANCE_URL = "https://api.stripe.com/v1/balance";
    static final String PAYPAL_SANDBOX_TOKEN_URL = "https://api-m.sandbox.paypal.com/v1/oauth2/token";
    static final String PAYPAL_LIVE_TOKEN_URL = "https://api-m.paypal.com/v1/oauth2/token";
    static final String TEST_ADDRESS = "Santo Domingo, Dominican Republic";
    static final String FORMAT_ONLY_MESSAGE = "Formato validado; la conectividad con este proveedor no se puede "
            + "verificar sin una transacción";
    private static final String NETWORK_ERROR = "No se pudo conectar con el proveedor (tiempo de espera o error de "
            + "red).";

    private final SecretCipher secretCipher;
    private final WhatsAppClient whatsAppClient;

    @Setter(AccessLevel.PACKAGE)
    private RestTemplate restTemplate = IntegrationHttp.buildRestTemplate();

    // ---------------------------------------------------------------- maps

    public IntegrationTestResultDTO testMaps(IntegrationConfig config) {
        if (config.getMapsProvider() == null || isBlank(config.getMapsApiKeyEnc())) {
            return fail("Configura el proveedor y la API key de mapas antes de probar.");
        }
        String apiKey;
        try {
            apiKey = secretCipher.decrypt(config.getMapsApiKeyEnc());
        } catch (CustomException e) {
            return fail(e.getMessage());
        }
        try {
            return switch (config.getMapsProvider()) {
                case GOOGLE_MAPS -> testGoogleMaps(apiKey);
                case MAPBOX -> testMapbox(apiKey);
            };
        } catch (RestClientException e) {
            log.warn("Maps connection test failed: {}", e.getClass().getSimpleName());
            return fail(NETWORK_ERROR);
        } catch (RuntimeException e) {
            log.warn("Maps connection test failed: {}", e.getClass().getSimpleName());
            return fail("Error inesperado al probar la conexión.");
        }
    }

    private IntegrationTestResultDTO testGoogleMaps(String apiKey) {
        URI uri = UriComponentsBuilder.fromUriString(GOOGLE_GEOCODE_URL)
                .queryParam("address", "{address}")
                .queryParam("key", "{key}")
                .encode().buildAndExpand(TEST_ADDRESS, apiKey).toUri();
        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                String.class);
        JsonNode json = IntegrationHttp.parseJson(response.getBody());
        if (json == null) {
            return fail("Respuesta inesperada de Google Maps (HTTP " + response.getStatusCode().value() + ").");
        }
        String status = json.path("status").asText("");
        if ("OK".equals(status) || "ZERO_RESULTS".equals(status)) {
            return ok("Conexión con Google Maps correcta.");
        }
        String error = IntegrationHttp.safeMessage(json.path("error_message").asText(""), apiKey);
        return fail("Google Maps rechazó la solicitud (" + status + ")" + (error.isEmpty() ? "." : ": " + error));
    }

    private IntegrationTestResultDTO testMapbox(String apiKey) {
        URI uri = UriComponentsBuilder.fromUriString(MAPBOX_GEOCODE_URL)
                .queryParam("limit", "1")
                .queryParam("access_token", "{token}")
                .encode().buildAndExpand(TEST_ADDRESS, apiKey).toUri();
        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                String.class);
        int status = response.getStatusCode().value();
        if (response.getStatusCode().is2xxSuccessful()) {
            return ok("Conexión con Mapbox correcta.");
        }
        if (status == 401 || status == 403) {
            return fail("Mapbox rechazó el token de acceso (HTTP " + status + "): API key inválida.");
        }
        return fail("Mapbox respondió con un error (HTTP " + status + ").");
    }

    // ------------------------------------------------------------ payments

    public IntegrationTestResultDTO testPayments(IntegrationConfig config) {
        if (config.getPaymentProvider() == null || isBlank(config.getPaymentSecretKeyEnc())) {
            return fail("Configura el proveedor y la clave secreta de pagos antes de probar.");
        }
        String secret;
        try {
            secret = secretCipher.decrypt(config.getPaymentSecretKeyEnc());
        } catch (CustomException e) {
            return fail(e.getMessage());
        }
        PaymentEnvironment environment = config.getPaymentEnvironment() != null ? config.getPaymentEnvironment()
                : PaymentEnvironment.SANDBOX;
        try {
            return switch (config.getPaymentProvider()) {
                case STRIPE -> testStripe(secret, environment);
                case PAYPAL -> testPaypal(config.getPaymentPublicKey(), secret, environment);
                case AZUL, CARDNET -> validateFormatOnly(config.getPaymentPublicKey(), secret);
            };
        } catch (RestClientException e) {
            log.warn("Payments connection test failed: {}", e.getClass().getSimpleName());
            return fail(NETWORK_ERROR);
        } catch (RuntimeException e) {
            log.warn("Payments connection test failed: {}", e.getClass().getSimpleName());
            return fail("Error inesperado al probar la conexión.");
        }
    }

    private IntegrationTestResultDTO testStripe(String secret, PaymentEnvironment environment) {
        boolean testKey = secret.startsWith("sk_test_") || secret.startsWith("rk_test_");
        boolean liveKey = secret.startsWith("sk_live_") || secret.startsWith("rk_live_");
        if (environment == PaymentEnvironment.SANDBOX && !testKey) {
            return fail("El entorno es SANDBOX pero la clave no es de pruebas (debe empezar por sk_test_ o "
                    + "rk_test_).");
        }
        if (environment == PaymentEnvironment.PRODUCTION && !liveKey) {
            return fail("El entorno es PRODUCTION pero la clave no es de producción (debe empezar por sk_live_ o "
                    + "rk_live_).");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secret);
        ResponseEntity<String> response = restTemplate.exchange(URI.create(STRIPE_BALANCE_URL), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        int status = response.getStatusCode().value();
        if (response.getStatusCode().is2xxSuccessful()) {
            return ok("Conexión con Stripe correcta (" + environment + ").");
        }
        if (status == 401) {
            return fail("Stripe rechazó la clave secreta (HTTP 401): credenciales inválidas.");
        }
        return fail("Stripe respondió con un error (HTTP " + status + ").");
    }

    private IntegrationTestResultDTO testPaypal(String clientId, String secret, PaymentEnvironment environment) {
        if (isBlank(clientId)) {
            return fail("Falta el Client ID de PayPal (clave pública).");
        }
        String url = environment == PaymentEnvironment.PRODUCTION ? PAYPAL_LIVE_TOKEN_URL : PAYPAL_SANDBOX_TOKEN_URL;
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(clientId, secret);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        ResponseEntity<String> response = restTemplate.exchange(URI.create(url), HttpMethod.POST,
                new HttpEntity<>(form, headers), String.class);
        int status = response.getStatusCode().value();
        if (response.getStatusCode().is2xxSuccessful()) {
            return ok("Conexión con PayPal correcta (" + environment + ").");
        }
        if (status == 401) {
            return fail("PayPal rechazó las credenciales (HTTP 401): Client ID o secreto inválidos.");
        }
        return fail("PayPal respondió con un error (HTTP " + status + ").");
    }

    private IntegrationTestResultDTO validateFormatOnly(String publicKey, String secret) {
        if (isBlank(publicKey)) {
            return fail("Falta la clave pública / identificador del comercio.");
        }
        if (containsWhitespace(publicKey) || containsWhitespace(secret) || secret.length() < 8) {
            return fail("El formato de las credenciales no es válido (sin espacios y con al menos 8 caracteres en "
                    + "la clave secreta).");
        }
        return ok(FORMAT_ONLY_MESSAGE);
    }

    // ------------------------------------------------------------ whatsapp

    public IntegrationTestResultDTO testWhatsapp(IntegrationConfig config, String phone) {
        return whatsAppClient.send(config, phone, WhatsAppClient.TEST_MESSAGE);
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static IntegrationTestResultDTO ok(String message) {
        return new IntegrationTestResultDTO(true, message);
    }

    private static IntegrationTestResultDTO fail(String message) {
        return new IntegrationTestResultDTO(false, message);
    }
}
