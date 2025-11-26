package org.example.cw3ilp.api.model;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.cw3ilp.api.validation.ValidPosition;

@Data
@ValidPosition // custom annotation to ensure coords are valid
public class LngLat {

    @NotNull
    private Double lng;
    @NotNull
    private Double lat;

    public LngLat(Double lng, Double lat) {
        this.lng = lng;
        this.lat = lat;
    }

}