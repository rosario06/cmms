package com.grash.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IotTelemetryPostDTO {
    @NotBlank
    @Size(max = 100)
    private String assetCode;

    @NotBlank
    @Size(max = 100)
    private String metricName;

    @NotNull
    private Double value;

    /**
     * ISO-8601 instant or offset date-time. Optional; defaults to the reception time.
     */
    @Size(max = 40)
    private String timestamp;

    @JsonIgnore
    @AssertTrue(message = "value must be a finite number")
    public boolean isValueFinite() {
        return value == null || Double.isFinite(value);
    }
}
