package com.grash.dto.integration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationShowDTO {
    private IotIntegrationShowDTO iot;
    private MapsIntegrationShowDTO maps;
    private PaymentsIntegrationShowDTO payments;
    private NotificationsIntegrationShowDTO notifications;
}
