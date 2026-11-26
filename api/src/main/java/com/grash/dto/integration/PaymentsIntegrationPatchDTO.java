package com.grash.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.grash.model.enums.PaymentEnvironment;
import com.grash.model.enums.PaymentProvider;
import lombok.Data;

/**
 * Secrets (secretKey, webhookSecret): absent/null = unchanged, non-empty = replace, "" = delete.
 * platformCommissionPercent: absent = unchanged, explicit null = clear.
 */
@Data
public class PaymentsIntegrationPatchDTO {
    private Boolean enabled;
    private PaymentProvider provider;
    private PaymentEnvironment environment;
    private String publicKey;
    private String secretKey;
    private String webhookSecret;
    private Double platformCommissionPercent;

    @JsonIgnore
    private boolean platformCommissionPercentSet;

    public void setPlatformCommissionPercent(Double platformCommissionPercent) {
        this.platformCommissionPercent = platformCommissionPercent;
        this.platformCommissionPercentSet = true;
    }
}
