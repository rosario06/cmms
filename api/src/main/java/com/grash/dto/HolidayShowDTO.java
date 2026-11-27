package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "DTO for displaying a holiday")
public class HolidayShowDTO extends AuditShowDTO {
    private String name;

    @Schema(type = "string", format = "date", example = "2026-12-25")
    private LocalDate date;

    private boolean recurringAnnually;

    @Schema(description = "Null when the holiday applies to every schedule")
    private WorkScheduleMiniDTO workSchedule;
}
