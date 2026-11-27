package com.grash.dto.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "QR attendance record (check-in / check-out) of a technician in a work order")
public class AttendanceLogShowDTO {
    private Long id;
    private Long workOrderId;
    private Long technicianId;
    private String technicianName;
    private Long assetId;
    private Date checkInTime;
    private Date checkOutTime;
    private Double checkInLat;
    private Double checkInLng;
    private Double checkOutLat;
    private Double checkOutLng;
    private String qrCodeScanned;
    private Long totalMinutes;
    private String notes;
    @Schema(description = "True while the technician has not checked out (checkOutTime is null)")
    private boolean active;
}
