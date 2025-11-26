package org.example.cw3ilp.api.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.cw3ilp.api.model.LngLat;


@Data
public class DistanceRequest {

    @NotNull
    @Valid
    private LngLat lngLat1;
    @NotNull
    @Valid
    private LngLat lngLat2;

    public DistanceRequest() {}

    public LngLat getPosition1() {
        return lngLat1;
    }
    public void setPosition1(LngLat lngLat1) {
        this.lngLat1 = lngLat1;
    }
    public LngLat getPosition2() {
        return lngLat2;
    }
    public void setPosition2(LngLat lngLat2) {
        this.lngLat2 = lngLat2;
    }
}
