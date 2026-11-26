package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = "iotTokenHash")
public class IntegrationConfig extends CompanyAudit {
    private boolean iotEnabled = false;

    /**
     * Hash of the IoT ingestion token (see Helper.hashKey). The clear token is never stored.
     */
    private String iotTokenHash;

    private String iotTokenLast4;

    private String webhookAlertUrl;
}
