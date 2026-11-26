package com.grash.dto.integration;

import lombok.Data;

@Data
public class IotIntegrationShowDTO {
    private boolean enabled;
    private boolean hasToken;
    private String tokenLast4;
    private String webhookAlertUrl;
    private String ingestUrl;
}
