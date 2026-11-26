package com.grash.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.grash.model.enums.MapsProvider;
import lombok.Data;

/**
 * Secrets: absent/null = unchanged, non-empty = replace, "" = delete.
 * Numeric fields: absent = unchanged, explicit null = clear.
 */
@Data
public class MapsIntegrationPatchDTO {
    private Boolean enabled;
    private MapsProvider provider;
    private String apiKey;
    private Double defaultCenterLat;
    private Double defaultCenterLng;
    private Double searchRadiusKm;

    @JsonIgnore
    private boolean defaultCenterLatSet;
    @JsonIgnore
    private boolean defaultCenterLngSet;
    @JsonIgnore
    private boolean searchRadiusKmSet;

    public void setDefaultCenterLat(Double defaultCenterLat) {
        this.defaultCenterLat = defaultCenterLat;
        this.defaultCenterLatSet = true;
    }

    public void setDefaultCenterLng(Double defaultCenterLng) {
        this.defaultCenterLng = defaultCenterLng;
        this.defaultCenterLngSet = true;
    }

    public void setSearchRadiusKm(Double searchRadiusKm) {
        this.searchRadiusKm = searchRadiusKm;
        this.searchRadiusKmSet = true;
    }
}
