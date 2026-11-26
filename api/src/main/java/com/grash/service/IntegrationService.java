package com.grash.service;

import com.grash.dto.integration.IotIntegrationPatchDTO;
import com.grash.dto.integration.IotTokenDTO;
import com.grash.dto.integration.MapsIntegrationPatchDTO;
import com.grash.dto.integration.NotificationsIntegrationPatchDTO;
import com.grash.dto.integration.PaymentsIntegrationPatchDTO;
import com.grash.exception.CustomException;
import com.grash.model.Company;
import com.grash.model.IntegrationConfig;
import com.grash.model.IotTelemetryLog;
import com.grash.model.enums.MapsProvider;
import com.grash.model.enums.PaymentProvider;
import com.grash.model.enums.WhatsappProvider;
import com.grash.repository.IntegrationConfigRepository;
import com.grash.repository.IotTelemetryLogRepository;
import com.grash.utils.Helper;
import com.grash.utils.SecretCipher;
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
    private static final int MAX_SECRET_LENGTH = 4096;
    private static final int MAX_PUBLIC_VALUE_LENGTH = 255;
    private static final double MAX_SEARCH_RADIUS_KM = 500;

    private final IntegrationConfigRepository integrationConfigRepository;
    private final IotTelemetryLogRepository iotTelemetryLogRepository;
    private final SecretCipher secretCipher;
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

    @Transactional
    public IntegrationConfig updateMaps(Company company, MapsIntegrationPatchDTO patch) {
        IntegrationConfig config = getOrCreate(company);

        if (patch.isDefaultCenterLatSet() && patch.getDefaultCenterLat() != null) {
            requireRange(patch.getDefaultCenterLat(), -90, 90, "defaultCenterLat");
        }
        if (patch.isDefaultCenterLngSet() && patch.getDefaultCenterLng() != null) {
            requireRange(patch.getDefaultCenterLng(), -180, 180, "defaultCenterLng");
        }
        if (patch.isSearchRadiusKmSet() && patch.getSearchRadiusKm() != null) {
            Double radius = patch.getSearchRadiusKm();
            if (!(radius > 0 && radius <= MAX_SEARCH_RADIUS_KM)) {
                throw badRequest("searchRadiusKm must be greater than 0 and at most " + MAX_SEARCH_RADIUS_KM);
            }
        }

        String apiKey = normalizeSecret(patch.getApiKey(), "apiKey");
        boolean willHaveKey = apiKey == null ? hasText(config.getMapsApiKeyEnc()) : !apiKey.isEmpty();
        boolean willBeEnabled = patch.getEnabled() != null ? patch.getEnabled() : config.isMapsEnabled();
        MapsProvider provider = patch.getProvider() != null ? patch.getProvider() : config.getMapsProvider();
        if (willBeEnabled && (provider == null || !willHaveKey)) {
            throw badRequest("Maps cannot be enabled without a provider and an API key");
        }
        // encrypt before mutating anything: fails with 409 when the encryption key is not configured
        String apiKeyEnc = apiKey != null && !apiKey.isEmpty() ? secretCipher.encrypt(apiKey) : null;

        if (patch.getEnabled() != null) {
            config.setMapsEnabled(patch.getEnabled());
        }
        if (patch.getProvider() != null) {
            config.setMapsProvider(patch.getProvider());
        }
        if (apiKey != null) {
            config.setMapsApiKeyEnc(apiKeyEnc);
            config.setMapsApiKeyLast4(apiKeyEnc == null ? null : SecretCipher.last4(apiKey));
        }
        if (patch.isDefaultCenterLatSet()) {
            config.setDefaultCenterLat(patch.getDefaultCenterLat());
        }
        if (patch.isDefaultCenterLngSet()) {
            config.setDefaultCenterLng(patch.getDefaultCenterLng());
        }
        if (patch.isSearchRadiusKmSet()) {
            config.setSearchRadiusKm(patch.getSearchRadiusKm());
        }
        return integrationConfigRepository.save(config);
    }

    @Transactional
    public IntegrationConfig updatePayments(Company company, PaymentsIntegrationPatchDTO patch) {
        IntegrationConfig config = getOrCreate(company);

        if (patch.isPlatformCommissionPercentSet() && patch.getPlatformCommissionPercent() != null) {
            requireRange(patch.getPlatformCommissionPercent(), 0, 100, "platformCommissionPercent");
        }
        String publicKey = normalizeText(patch.getPublicKey(), MAX_PUBLIC_VALUE_LENGTH, "publicKey");
        String secretKey = normalizeSecret(patch.getSecretKey(), "secretKey");
        String webhookSecret = normalizeSecret(patch.getWebhookSecret(), "webhookSecret");

        boolean willHaveSecret = secretKey == null ? hasText(config.getPaymentSecretKeyEnc()) : !secretKey.isEmpty();
        boolean willBeEnabled = patch.getEnabled() != null ? patch.getEnabled() : config.isPaymentsEnabled();
        PaymentProvider provider = patch.getProvider() != null ? patch.getProvider() : config.getPaymentProvider();
        if (willBeEnabled && (provider == null || !willHaveSecret)) {
            throw badRequest("Payments cannot be enabled without a provider and a secret key");
        }
        String secretKeyEnc = secretKey != null && !secretKey.isEmpty() ? secretCipher.encrypt(secretKey) : null;
        String webhookSecretEnc = webhookSecret != null && !webhookSecret.isEmpty()
                ? secretCipher.encrypt(webhookSecret) : null;

        if (patch.getEnabled() != null) {
            config.setPaymentsEnabled(patch.getEnabled());
        }
        if (patch.getProvider() != null) {
            config.setPaymentProvider(patch.getProvider());
        }
        if (patch.getEnvironment() != null) {
            config.setPaymentEnvironment(patch.getEnvironment());
        }
        if (publicKey != null) {
            config.setPaymentPublicKey(publicKey.isEmpty() ? null : publicKey);
        }
        if (secretKey != null) {
            config.setPaymentSecretKeyEnc(secretKeyEnc);
            config.setPaymentSecretKeyLast4(secretKeyEnc == null ? null : SecretCipher.last4(secretKey));
        }
        if (webhookSecret != null) {
            config.setPaymentWebhookSecretEnc(webhookSecretEnc);
            config.setPaymentWebhookSecretLast4(webhookSecretEnc == null ? null : SecretCipher.last4(webhookSecret));
        }
        if (patch.isPlatformCommissionPercentSet()) {
            config.setPlatformCommissionPercent(patch.getPlatformCommissionPercent());
        }
        return integrationConfigRepository.save(config);
    }

    @Transactional
    public IntegrationConfig updateNotifications(Company company, NotificationsIntegrationPatchDTO patch) {
        IntegrationConfig config = getOrCreate(company);

        String phoneNumberId = normalizeText(patch.getPhoneNumberId(), MAX_PUBLIC_VALUE_LENGTH, "phoneNumberId");
        String accountSid = normalizeText(patch.getAccountSid(), MAX_PUBLIC_VALUE_LENGTH, "accountSid");
        String accessToken = normalizeSecret(patch.getAccessToken(), "accessToken");

        boolean willHavePhoneId = phoneNumberId == null ? hasText(config.getWhatsappPhoneNumberId())
                : !phoneNumberId.isEmpty();
        boolean willHaveToken = accessToken == null ? hasText(config.getWhatsappAccessTokenEnc())
                : !accessToken.isEmpty();
        boolean willBeEnabled = patch.getWhatsappEnabled() != null ? patch.getWhatsappEnabled()
                : config.isWhatsappEnabled();
        WhatsappProvider provider = patch.getProvider() != null ? patch.getProvider() : config.getWhatsappProvider();
        if (willBeEnabled && (provider == null || !willHavePhoneId || !willHaveToken)) {
            throw badRequest("WhatsApp cannot be enabled without a provider, a phone number id and an access token");
        }
        String accessTokenEnc = accessToken != null && !accessToken.isEmpty() ? secretCipher.encrypt(accessToken)
                : null;

        if (patch.getWhatsappEnabled() != null) {
            config.setWhatsappEnabled(patch.getWhatsappEnabled());
        }
        if (patch.getProvider() != null) {
            config.setWhatsappProvider(patch.getProvider());
        }
        if (phoneNumberId != null) {
            config.setWhatsappPhoneNumberId(phoneNumberId.isEmpty() ? null : phoneNumberId);
        }
        if (accountSid != null) {
            config.setWhatsappAccountSid(accountSid.isEmpty() ? null : accountSid);
        }
        if (accessToken != null) {
            config.setWhatsappAccessTokenEnc(accessTokenEnc);
            config.setWhatsappAccessTokenLast4(accessTokenEnc == null ? null : SecretCipher.last4(accessToken));
        }
        if (patch.getNotifyOnWorkOrderCreated() != null) {
            config.setNotifyOnWorkOrderCreated(patch.getNotifyOnWorkOrderCreated());
        }
        if (patch.getNotifyOnTechnicianAssigned() != null) {
            config.setNotifyOnTechnicianAssigned(patch.getNotifyOnTechnicianAssigned());
        }
        if (patch.getNotifyOnTechnicianArrived() != null) {
            config.setNotifyOnTechnicianArrived(patch.getNotifyOnTechnicianArrived());
        }
        if (patch.getNotifyOnWorkOrderCompleted() != null) {
            config.setNotifyOnWorkOrderCompleted(patch.getNotifyOnWorkOrderCompleted());
        }
        return integrationConfigRepository.save(config);
    }

    /**
     * null = unchanged, "" = delete, otherwise the trimmed value.
     */
    private static String normalizeSecret(String value, String field) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_SECRET_LENGTH) {
            throw badRequest(field + " is invalid");
        }
        return trimmed;
    }

    /**
     * null = unchanged, "" (or blank) = clear, otherwise the trimmed value.
     */
    private static String normalizeText(String value, int maxLength, String field) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw badRequest(field + " is too long");
        }
        return trimmed;
    }

    private static void requireRange(double value, double min, double max, String field) {
        if (!(value >= min && value <= max)) {
            throw badRequest(field + " must be between " + min + " and " + max);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private static CustomException badRequest(String message) {
        return new CustomException(message, HttpStatus.BAD_REQUEST);
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
