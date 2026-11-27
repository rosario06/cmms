package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Every field is optional: a null field is left unchanged.
 */
@Data
@NoArgsConstructor
@Schema(description = "DTO for patching a holiday (null fields are not modified)")
public class HolidayPatchDTO {
    private String name;

    @Schema(type = "string", format = "date", example = "2026-12-25")
    private LocalDate date;

    private Boolean recurringAnnually;

    @Schema(description = "Schedule; send an object without id to make the holiday apply to every schedule",
            implementation = IdDTO.class)
    private IdDTO workSchedule;
}
