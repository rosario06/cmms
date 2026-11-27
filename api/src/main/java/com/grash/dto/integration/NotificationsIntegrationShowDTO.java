package com.grash.dto.integration;

import com.grash.model.enums.WhatsappProvider;
import lombok.Data;

@Data
public class NotificationsIntegrationShowDTO {
    private boolean whatsappEnabled;
    private WhatsappProvider provider;
    private String phoneNumberId;
    private String accountSid;
    private boolean hasAccessToken;
    private String accessTokenLast4;
    private boolean notifyOnWorkOrderCreated;
    private boolean notifyOnTechnicianAssigned;
    private boolean notifyOnTechnicianArrived;
    private boolean notifyOnWorkOrderCompleted;
}
