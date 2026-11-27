package com.grash.dto.integration;

import com.grash.model.enums.MapsProvider;
import lombok.Data;

@Data
public class MapsIntegrationShowDTO {
    private boolean enabled;
    private MapsProvider provider;
    private boolean hasApiKey;
    private String apiKeyLast4;
    private Double defaultCenterLat;
    private Double defaultCenterLng;
    private Double searchRadiusKm;
}
