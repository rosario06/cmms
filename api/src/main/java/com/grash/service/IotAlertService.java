package com.grash.service;

import com.grash.utils.WebhookUrlValidator;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Map;

/**
 * Sends the optional outgoing alert of the IoT integration. Failures are only logged.
 */
@Service
@Slf4j
public class IotAlertService {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;

    @Setter(AccessLevel.PACKAGE)
    private RestTemplate restTemplate = buildRestTemplate();

    @Async
    public void sendAlert(String url, Map<String, Object> payload) {
        try {
            WebhookUrlValidator.validate(url);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), String.class);
        } catch (Exception e) {
            log.warn("IoT alert delivery failed: {}", e.getMessage());
        }
    }

    private static RestTemplate buildRestTemplate() {
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
        return new RestTemplate(factory);
    }
}
