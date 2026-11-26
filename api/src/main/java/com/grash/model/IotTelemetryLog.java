package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.IotTelemetryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Date;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IotTelemetryLog extends CompanyAudit {
    private String assetCode;

    private String metricName;

    private Double value;

    private Date readingTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IotTelemetryStatus status;

    @Column(length = 1000)
    private String message;
}
