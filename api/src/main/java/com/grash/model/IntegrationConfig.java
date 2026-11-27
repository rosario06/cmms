package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.MapsProvider;
import com.grash.model.enums.PaymentEnvironment;
import com.grash.model.enums.PaymentProvider;
import com.grash.model.enums.WhatsappProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"iotTokenHash", "mapsApiKeyEnc", "paymentSecretKeyEnc",
        "paymentWebhookSecretEnc", "whatsappAccessTokenEnc"})
public class IntegrationConfig extends CompanyAudit {
    private boolean iotEnabled = false;

    /**
     * Hash of the IoT ingestion token (see Helper.hashKey). The clear token is never stored.
     */
    private String iotTokenHash;

    private String iotTokenLast4;

    private String webhookAlertUrl;

    // ---- Maps ----
    private boolean mapsEnabled = false;

    @Enumerated(EnumType.STRING)
    private MapsProvider mapsProvider;

    /**
     * Encrypted (see SecretCipher). Never returned by the API.
     */
    @Column(columnDefinition = "TEXT")
    private String mapsApiKeyEnc;

    private String mapsApiKeyLast4;

    private Double defaultCenterLat;

    private Double defaultCenterLng;

    private Double searchRadiusKm;

    // ---- Payments ----
    private boolean paymentsEnabled = false;

    @Enumerated(EnumType.STRING)
    private PaymentProvider paymentProvider;

    @Enumerated(EnumType.STRING)
    private PaymentEnvironment paymentEnvironment = PaymentEnvironment.SANDBOX;

    /**
     * Public (publishable) key: not a secret.
     */
    private String paymentPublicKey;

    @Column(columnDefinition = "TEXT")
    private String paymentSecretKeyEnc;

    private String paymentSecretKeyLast4;

    @Column(columnDefinition = "TEXT")
    private String paymentWebhookSecretEnc;

    private String paymentWebhookSecretLast4;

    private Double platformCommissionPercent;

    // ---- Notifications (WhatsApp) ----
    private boolean whatsappEnabled = false;

    @Enumerated(EnumType.STRING)
    private WhatsappProvider whatsappProvider;

    private String whatsappPhoneNumberId;

    /**
     * Twilio only. Not a secret.
     */
    private String whatsappAccountSid;

    @Column(columnDefinition = "TEXT")
    private String whatsappAccessTokenEnc;

    private String whatsappAccessTokenLast4;

    private boolean notifyOnWorkOrderCreated = false;

    private boolean notifyOnTechnicianAssigned = false;

    private boolean notifyOnTechnicianArrived = false;

    private boolean notifyOnWorkOrderCompleted = false;
}
