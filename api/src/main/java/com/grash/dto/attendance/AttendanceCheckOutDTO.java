package com.grash.dto.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Check-out request")
public class AttendanceCheckOutDTO {
    @Schema(description = "Optional QR content; if present it is validated like in the check-in")
    private String code;
    private Double latitude;
    private Double longitude;
    private String notes;
}
