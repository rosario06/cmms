package com.grash.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IotIntegrationPatchDTO {
    private Boolean enabled;

    @Size(max = 2048)
    private String webhookAlertUrl;

    /**
     * True when the client sent the webhookAlertUrl property (even as null/blank, which clears it).
     */
    @JsonIgnore
    private boolean webhookAlertUrlSet;

    public void setWebhookAlertUrl(String webhookAlertUrl) {
        this.webhookAlertUrl = webhookAlertUrl;
        this.webhookAlertUrlSet = true;
    }
}
