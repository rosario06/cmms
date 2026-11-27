package com.grash.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@Schema(description = "A working span of a work schedule. When endTime <= startTime it ends the next day")
public class WorkScheduleShiftDTO {
    @Schema(description = "Unique identifier (ignored on input: shifts are replaced as a whole)",
            accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "Optional name, e.g. Turno B")
    private String name;

    @NotNull
    @Min(1)
    @Max(7)
    @Schema(description = "ISO day of week, 1 = Monday ... 7 = Sunday", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer dayOfWeek;

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    @Schema(type = "string", example = "08:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalTime startTime;

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    @Schema(type = "string", example = "17:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalTime endTime;
}
