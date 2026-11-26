package com.grash.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * Shared helpers for the outgoing calls of the integrations (connection tests and WhatsApp delivery).
 * Only fixed provider hosts must be called with this client, never user-controlled URLs.
 */
public final class IntegrationHttp {

    public static final int CONNECT_TIMEOUT_MS = 5000;
    public static final int READ_TIMEOUT_MS = 10000;
    private static final int MAX_MESSAGE_LENGTH = 200;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private IntegrationHttp() {
    }

    /**
     * RestTemplate with 5s connect / 10s read timeouts, no redirects and no exception on 4xx/5xx
     * (callers inspect the status code).
     */
    public static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                // never follow redirects (SSRF hardening)
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        // streaming mode makes HttpURLConnection throw on 401 responses to POST requests
        factory.setOutputStreaming(false);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return restTemplate;
    }

    /**
     * Parses a JSON body, returning null when it is not valid JSON.
     */
    public static JsonNode parseJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(body);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Text that is safe to show to the user: secrets are removed and the length is capped.
     */
    public static String safeMessage(String raw, String... secrets) {
        if (raw == null) {
            return "";
        }
        String result = raw;
        if (secrets != null) {
            for (String secret : secrets) {
                if (secret != null && !secret.isEmpty()) {
                    result = result.replace(secret, "***");
                }
            }
        }
        result = result.replaceAll("[\\r\\n]+", " ").trim();
        return result.length() > MAX_MESSAGE_LENGTH ? result.substring(0, MAX_MESSAGE_LENGTH) + "..." : result;
    }
}
