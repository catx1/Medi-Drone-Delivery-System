package org.example.cw3ilp.api.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LngLatAlt {
    @NotNull
    private Double lng;
    @NotNull
    private Double lat;
    @NotNull
    private Double alt;

    // ignore alt - not used in this cw
    public LngLatAlt(double newLng, double newLat, Double alt) {
        this.lng = newLng;
        this.lat = newLat;
        this.alt = 0.0;
    }

}
