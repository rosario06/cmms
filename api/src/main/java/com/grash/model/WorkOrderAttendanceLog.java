package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Date;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"workOrder", "technician", "asset"})
public class WorkOrderAttendanceLog extends CompanyAudit {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private WorkOrder workOrder;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private User technician;

    @ManyToOne(fetch = FetchType.LAZY)
    private Asset asset;

    @NotNull
    @Column(nullable = false)
    private Date checkInTime;

    private Date checkOutTime;

    private Double checkInLat;

    private Double checkInLng;

    private Double checkOutLat;

    private Double checkOutLng;

    @Column(length = 500)
    private String qrCodeScanned;

    private Long totalMinutes;

    @Column(length = 1000)
    private String notes;
}
