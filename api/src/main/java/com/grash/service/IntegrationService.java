package com.grash.service;

import com.grash.dto.integration.IotIntegrationPatchDTO;
import com.grash.dto.integration.IotTokenDTO;
import com.grash.exception.CustomException;
import com.grash.model.Company;
import com.grash.model.IntegrationConfig;
import com.grash.model.IotTelemetryLog;
import com.grash.repository.IntegrationConfigRepository;
import com.grash.repository.IotTelemetryLogRepository;
import com.grash.utils.Helper;
import com.grash.utils.WebhookUrlValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IntegrationService {

    public static final String IOT_TOKEN_PREFIX = "iot_live_";
    static final int MAX_TELEMETRY_SIZE = 100;
    static final int DEFAULT_TELEMETRY_SIZE = 20;
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_URL_LENGTH = 2048;

    private final IntegrationConfigRepository integrationConfigRepository;
    private final IotTelemetryLogRepository iotTelemetryLogRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Returns the integration config of the company, creating it lazily on first access.
     */
    public IntegrationConfig getOrCreate(Company company) {
        return integrationConfigRepository.findByCompanyId(company.getId()).orElseGet(() -> {
            IntegrationConfig config = new IntegrationConfig();
            config.setCompany(company);
            try {
                return integrationConfigRepository.saveAndFlush(config);
            } catch (DataIntegrityViolationException e) {
                // created concurrently by another request (unique constraint on company_id)
                return integrationConfigRepository.findByCompanyId(company.getId())
                        .orElseThrow(() -> e);
            }
        });
    }

    @Transactional
    public IntegrationConfig updateIot(Company company, IotIntegrationPatchDTO patch) {
        IntegrationConfig config = getOrCreate(company);
        if (patch.getEnabled() != null) {
            config.setIotEnabled(patch.getEnabled());
        }
        if (patch.isWebhookAlertUrlSet()) {
            String url = patch.getWebhookAlertUrl();
            if (url == null || url.isBlank()) {
                config.setWebhookAlertUrl(null);
            } else {
                url = url.trim();
                if (url.length() > MAX_URL_LENGTH) {
                    throw new CustomException("Webhook URL is too long", HttpStatus.BAD_REQUEST);
                }
                try {
                    WebhookUrlValidator.validate(url);
                } catch (WebhookUrlValidator.WebhookUrlValidationException e) {
                    throw new CustomException(e.getMessage(), HttpStatus.BAD_REQUEST);
                }
                config.setWebhookAlertUrl(url);
            }
        }
        return integrationConfigRepository.save(config);
    }

    /**
     * Generates a new token, stores only its hash and last 4 characters. The clear token is only returned here.
     * Any previous token stops working.
     */
    @Transactional
    public IotTokenDTO rotateIotToken(Company company) {
        IntegrationConfig config = getOrCreate(company);
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = IOT_TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String last4 = token.substring(token.length() - 4);
        config.setIotTokenHash(hash(token));
        config.setIotTokenLast4(last4);
        integrationConfigRepository.save(config);
        return new IotTokenDTO(token, last4);
    }

    public List<IotTelemetryLog> getLatestTelemetry(Long companyId, Integer size) {
        int effectiveSize = size == null ? DEFAULT_TELEMETRY_SIZE : Math.min(Math.max(size, 1), MAX_TELEMETRY_SIZE);
        return iotTelemetryLogRepository.findByCompanyIdOrderByCreatedAtDescIdDesc(companyId,
                PageRequest.of(0, effectiveSize));
    }

    static String hash(String token) {
        try {
            return Helper.hashKey(token);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
