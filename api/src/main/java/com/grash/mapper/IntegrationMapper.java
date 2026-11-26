package com.grash.mapper;

import com.grash.dto.integration.IotIntegrationShowDTO;
import com.grash.dto.integration.IotTelemetryLogShowDTO;
import com.grash.dto.integration.MapsIntegrationShowDTO;
import com.grash.dto.integration.NotificationsIntegrationShowDTO;
import com.grash.dto.integration.PaymentsIntegrationShowDTO;
import com.grash.model.IntegrationConfig;
import com.grash.model.IotTelemetryLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface IntegrationMapper {
    @Mapping(source = "iotEnabled", target = "enabled")
    @Mapping(target = "hasToken",
            expression = "java(model.getIotTokenHash() != null && !model.getIotTokenHash().isEmpty())")
    @Mapping(source = "iotTokenLast4", target = "tokenLast4")
    @Mapping(target = "ingestUrl", ignore = true)
    IotIntegrationShowDTO toIotShowDto(IntegrationConfig model);

    @Mapping(source = "mapsEnabled", target = "enabled")
    @Mapping(source = "mapsProvider", target = "provider")
    @Mapping(target = "hasApiKey",
            expression = "java(model.getMapsApiKeyEnc() != null && !model.getMapsApiKeyEnc().isEmpty())")
    @Mapping(source = "mapsApiKeyLast4", target = "apiKeyLast4")
    MapsIntegrationShowDTO toMapsShowDto(IntegrationConfig model);

    @Mapping(source = "paymentsEnabled", target = "enabled")
    @Mapping(source = "paymentProvider", target = "provider")
    @Mapping(source = "paymentEnvironment", target = "environment")
    @Mapping(source = "paymentPublicKey", target = "publicKey")
    @Mapping(target = "hasSecretKey",
            expression = "java(model.getPaymentSecretKeyEnc() != null && !model.getPaymentSecretKeyEnc().isEmpty())")
    @Mapping(source = "paymentSecretKeyLast4", target = "secretKeyLast4")
    @Mapping(target = "hasWebhookSecret",
            expression = "java(model.getPaymentWebhookSecretEnc() != null && !model.getPaymentWebhookSecretEnc().isEmpty())")
    @Mapping(source = "paymentWebhookSecretLast4", target = "webhookSecretLast4")
    PaymentsIntegrationShowDTO toPaymentsShowDto(IntegrationConfig model);

    @Mapping(source = "whatsappProvider", target = "provider")
    @Mapping(source = "whatsappPhoneNumberId", target = "phoneNumberId")
    @Mapping(source = "whatsappAccountSid", target = "accountSid")
    @Mapping(target = "hasAccessToken",
            expression = "java(model.getWhatsappAccessTokenEnc() != null && !model.getWhatsappAccessTokenEnc().isEmpty())")
    @Mapping(source = "whatsappAccessTokenLast4", target = "accessTokenLast4")
    NotificationsIntegrationShowDTO toNotificationsShowDto(IntegrationConfig model);

    @Mapping(source = "readingTimestamp", target = "timestamp")
    IotTelemetryLogShowDTO toTelemetryShowDto(IotTelemetryLog model);
}
