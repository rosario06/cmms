package com.grash.service;

import com.grash.dto.integration.IntegrationTestResultDTO;
import com.grash.exception.CustomException;
import com.grash.model.IntegrationConfig;
import com.grash.model.enums.MapsProvider;
import com.grash.model.enums.PaymentEnvironment;
import com.grash.model.enums.PaymentProvider;
import com.grash.utils.SecretCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationTestServiceTest {

    private static final String SECRET = "TOPSECRET-value-1234";

    @Mock
    private SecretCipher secretCipher;
    @Mock
    private WhatsAppClient whatsAppClient;
    @Mock
    private RestTemplate restTemplate;

    private IntegrationTestService service;
    private IntegrationConfig config;

    @BeforeEach
    void setUp() {
        service = new IntegrationTestService(secretCipher, whatsAppClient);
        service.setRestTemplate(restTemplate);
        config = new IntegrationConfig();
        lenient().when(secretCipher.decrypt("v1:enc")).thenReturn(SECRET);
    }

    private void respond(HttpMethod method, int status, String body) {
        when(restTemplate.exchange(any(URI.class), eq(method), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.status(status).body(body));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private HttpEntity capturedEntity(URI[] uriHolder, HttpMethod method) {
        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(uriCaptor.capture(), eq(method), entityCaptor.capture(), eq(String.class));
        uriHolder[0] = uriCaptor.getValue();
        return entityCaptor.getValue();
    }

    // ------------------------------------------------------------------ maps

    @Test
    void maps_withoutProviderOrKey_failsWithoutCallingProvider() {
        IntegrationTestResultDTO result = service.testMaps(config);

        assertFalse(result.isSuccess());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void maps_google_statusOk_succeedsAndUsesFixedHost() {
        config.setMapsProvider(MapsProvider.GOOGLE_MAPS);
        config.setMapsApiKeyEnc("v1:enc");
        respond(HttpMethod.GET, 200, "{\"status\":\"OK\",\"results\":[]}");

        IntegrationTestResultDTO result = service.testMaps(config);

        assertTrue(result.isSuccess());
        URI[] uri = new URI[1];
        capturedEntity(uri, HttpMethod.GET);
        assertEquals("https", uri[0].getScheme());
        assertEquals("maps.googleapis.com", uri[0].getHost());
        assertEquals("/maps/api/geocode/json", uri[0].getPath());
        assertTrue(uri[0].getRawQuery().contains("key=" + SECRET));
    }

    @Test
    void maps_google_requestDenied_failsWithProviderMessageWithoutSecret() {
        config.setMapsProvider(MapsProvider.GOOGLE_MAPS);
        config.setMapsApiKeyEnc("v1:enc");
        respond(HttpMethod.GET, 200, "{\"status\":\"REQUEST_DENIED\",\"error_message\":\"The key " + SECRET
                + " is invalid.\"}");

        IntegrationTestResultDTO result = service.testMaps(config);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("REQUEST_DENIED"));
        assertFalse(result.getMessage().contains(SECRET));
    }

    @Test
    void maps_mapbox_200Succeeds_401IsInvalidKey() {
        config.setMapsProvider(MapsProvider.MAPBOX);
        config.setMapsApiKeyEnc("v1:enc");
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"))
                .thenReturn(ResponseEntity.status(401).body("{\"message\":\"Not Authorized\"}"));

        IntegrationTestResultDTO ok = service.testMaps(config);
        IntegrationTestResultDTO unauthorized = service.testMaps(config);

        assertTrue(ok.isSuccess());
        assertFalse(unauthorized.isSuccess());
        assertTrue(unauthorized.getMessage().contains("401"));
        assertFalse(unauthorized.getMessage().contains(SECRET));
    }

    @Test
    void maps_mapbox_usesFixedHost() {
        config.setMapsProvider(MapsProvider.MAPBOX);
        config.setMapsApiKeyEnc("v1:enc");
        respond(HttpMethod.GET, 200, "{}");

        service.testMaps(config);

        URI[] uri = new URI[1];
        capturedEntity(uri, HttpMethod.GET);
        assertEquals("api.mapbox.com", uri[0].getHost());
        assertTrue(uri[0].getPath().startsWith("/geocoding/v5/mapbox.places/"));
    }

    @Test
    void maps_networkError_messageDoesNotLeakUrlOrSecret() {
        config.setMapsProvider(MapsProvider.GOOGLE_MAPS);
        config.setMapsApiKeyEnc("v1:enc");
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("I/O error on GET request for \"https://x?key=" + SECRET
                        + "\": timed out"));

        IntegrationTestResultDTO result = service.testMaps(config);

        assertFalse(result.isSuccess());
        assertFalse(result.getMessage().contains(SECRET));
    }

    @Test
    void maps_missingEncryptionKey_reportsClearMessage() {
        config.setMapsProvider(MapsProvider.GOOGLE_MAPS);
        config.setMapsApiKeyEnc("v1:other");
        when(secretCipher.decrypt("v1:other")).thenThrow(
                new CustomException(SecretCipher.KEY_MISSING_MESSAGE, HttpStatus.CONFLICT));

        IntegrationTestResultDTO result = service.testMaps(config);

        assertFalse(result.isSuccess());
        assertEquals("INTEGRATIONS_ENCRYPTION_KEY no configurada", result.getMessage());
        verifyNoInteractions(restTemplate);
    }

    // -------------------------------------------------------------- payments

    @Test
    void payments_withoutSecret_failsWithoutCallingProvider() {
        config.setPaymentProvider(PaymentProvider.STRIPE);

        assertFalse(service.testPayments(config).isSuccess());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void payments_stripe_sandboxKey_succeedsWithBearerOnFixedHost() {
        config.setPaymentProvider(PaymentProvider.STRIPE);
        config.setPaymentEnvironment(PaymentEnvironment.SANDBOX);
        config.setPaymentSecretKeyEnc("v1:enc");
        when(secretCipher.decrypt("v1:enc")).thenReturn("sk_test_abcdef");
        respond(HttpMethod.GET, 200, "{}");

        IntegrationTestResultDTO result = service.testPayments(config);

        assertTrue(result.isSuccess());
        URI[] uri = new URI[1];
        HttpEntity<?> entity = capturedEntity(uri, HttpMethod.GET);
        assertEquals("api.stripe.com", uri[0].getHost());
        assertEquals("/v1/balance", uri[0].getPath());
        assertEquals("Bearer sk_test_abcdef", entity.getHeaders().getFirst("Authorization"));
    }

    @Test
    void payments_stripe_401_isInvalidCredentials() {
        config.setPaymentProvider(PaymentProvider.STRIPE);
        config.setPaymentSecretKeyEnc("v1:enc");
        when(secretCipher.decrypt("v1:enc")).thenReturn("rk_test_abcdef");
        respond(HttpMethod.GET, 401, "{\"error\":{\"message\":\"Invalid API Key provided: rk_test_abcdef\"}}");

        IntegrationTestResultDTO result = service.testPayments(config);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("401"));
        assertFalse(result.getMessage().contains("rk_test_abcdef"));
    }

    @Test
    void payments_stripe_prefixMismatchWithEnvironment_failsWithoutCalling() {
        config.setPaymentProvider(PaymentProvider.STRIPE);
        config.setPaymentEnvironment(PaymentEnvironment.SANDBOX);
        config.setPaymentSecretKeyEnc("v1:enc");
        when(secretCipher.decrypt("v1:enc")).thenReturn("sk_live_abcdef");

        IntegrationTestResultDTO sandboxWithLive = service.testPayments(config);

        assertFalse(sandboxWithLive.isSuccess());
        assertTrue(sandboxWithLive.getMessage().contains("SANDBOX"));
        assertFalse(sandboxWithLive.getMessage().contains("sk_live_abcdef"));

        config.setPaymentEnvironment(PaymentEnvironment.PRODUCTION);
        when(secretCipher.decrypt("v1:enc")).thenReturn("sk_test_abcdef");
        IntegrationTestResultDTO productionWithTest = service.testPayments(config);

        assertFalse(productionWithTest.isSuccess());
        assertTrue(productionWithTest.getMessage().contains("PRODUCTION"));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void payments_paypal_usesSandboxOrProductionHostWithBasicAuth() {
        config.setPaymentProvider(PaymentProvider.PAYPAL);
        config.setPaymentPublicKey("client-id");
        config.setPaymentSecretKeyEnc("v1:enc");
        config.setPaymentEnvironment(PaymentEnvironment.SANDBOX);
        respond(HttpMethod.POST, 200, "{\"access_token\":\"x\"}");

        assertTrue(service.testPayments(config).isSuccess());

        URI[] uri = new URI[1];
        HttpEntity<?> entity = capturedEntity(uri, HttpMethod.POST);
        assertEquals("api-m.sandbox.paypal.com", uri[0].getHost());
        assertEquals("/v1/oauth2/token", uri[0].getPath());
        String authorization = entity.getHeaders().getFirst("Authorization");
        assertNotNull(authorization);
        assertTrue(authorization.startsWith("Basic "));
    }

    @Test
    void payments_paypal_production_usesLiveHost_and401IsInvalid() {
        config.setPaymentProvider(PaymentProvider.PAYPAL);
        config.setPaymentPublicKey("client-id");
        config.setPaymentSecretKeyEnc("v1:enc");
        config.setPaymentEnvironment(PaymentEnvironment.PRODUCTION);
        respond(HttpMethod.POST, 401, "{\"error\":\"invalid_client\"}");

        IntegrationTestResultDTO result = service.testPayments(config);

        assertFalse(result.isSuccess());
        URI[] uri = new URI[1];
        capturedEntity(uri, HttpMethod.POST);
        assertEquals("api-m.paypal.com", uri[0].getHost());
    }

    @Test
    void payments_paypal_withoutClientId_fails() {
        config.setPaymentProvider(PaymentProvider.PAYPAL);
        config.setPaymentSecretKeyEnc("v1:enc");

        assertFalse(service.testPayments(config).isSuccess());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void payments_azulAndCardnet_onlyValidateFormatAndAdmitNoConnectivityCheck() {
        config.setPaymentPublicKey("merchant-1234");
        config.setPaymentSecretKeyEnc("v1:enc");
        for (PaymentProvider provider : new PaymentProvider[]{PaymentProvider.AZUL, PaymentProvider.CARDNET}) {
            config.setPaymentProvider(provider);

            IntegrationTestResultDTO result = service.testPayments(config);

            assertTrue(result.isSuccess());
            assertTrue(result.getMessage().contains("Formato validado; la conectividad con este proveedor no se "
                    + "puede verificar sin una transacción"));
        }
        verifyNoInteractions(restTemplate);
    }

    @Test
    void payments_azul_missingPublicKeyOrBadFormat_fails() {
        config.setPaymentProvider(PaymentProvider.AZUL);
        config.setPaymentSecretKeyEnc("v1:enc");

        assertFalse(service.testPayments(config).isSuccess());

        config.setPaymentPublicKey("merchant 1234");
        assertFalse(service.testPayments(config).isSuccess());
        verifyNoInteractions(restTemplate);
    }

    // ------------------------------------------------------------- whatsapp

    @Test
    void whatsapp_delegatesToClient() {
        IntegrationTestResultDTO expected = new IntegrationTestResultDTO(true, "ok");
        when(whatsAppClient.send(config, "+18095551234", WhatsAppClient.TEST_MESSAGE)).thenReturn(expected);

        assertEquals(expected, service.testWhatsapp(config, "+18095551234"));
    }
}
