package com.grash.dto.integration;

import com.grash.model.enums.WhatsappProvider;
import lombok.Data;

/**
 * accessToken: absent/null = unchanged, non-empty = replace, "" = delete.
 * phoneNumberId / accountSid: absent/null = unchanged, "" = clear.
 */
@Data
public class NotificationsIntegrationPatchDTO {
    private Boolean whatsappEnabled;
    private WhatsappProvider provider;
    private String phoneNumberId;
    private String accountSid;
    private String accessToken;
    private Boolean notifyOnWorkOrderCreated;
    private Boolean notifyOnTechnicianAssigned;
    private Boolean notifyOnTechnicianArrived;
    private Boolean notifyOnWorkOrderCompleted;
}
