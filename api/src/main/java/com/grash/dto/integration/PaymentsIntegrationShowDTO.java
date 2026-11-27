package com.grash.dto.integration;

import com.grash.model.enums.PaymentEnvironment;
import com.grash.model.enums.PaymentProvider;
import lombok.Data;

@Data
public class PaymentsIntegrationShowDTO {
    private boolean enabled;
    private PaymentProvider provider;
    private PaymentEnvironment environment;
    private String publicKey;
    private boolean hasSecretKey;
    private String secretKeyLast4;
    private boolean hasWebhookSecret;
    private String webhookSecretLast4;
    private Double platformCommissionPercent;
}
