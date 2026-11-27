package com.grash.dto.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Check-in request")
public class AttendanceCheckInDTO {
    @Schema(description = "Content read by the QR scanner (or typed by the technician)")
    private String code;
    private Double latitude;
    private Double longitude;
    private String notes;
}
