package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@Schema(description = "DTO for creating a holiday")
public class HolidayPostDTO {
    @NotBlank
    @Schema(description = "Holiday name", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull
    @Schema(type = "string", format = "date", example = "2026-12-25", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate date;

    @Schema(description = "Repeats every year on the same month and day")
    private boolean recurringAnnually;

    @Schema(description = "Schedule it applies to. Empty means every schedule of the company", implementation =
            IdDTO.class)
    private IdDTO workSchedule;
}
