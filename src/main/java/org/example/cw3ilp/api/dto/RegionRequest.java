package org.example.cw3ilp.api.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.cw3ilp.api.model.LngLat;
import org.example.cw3ilp.api.model.Region;

@Data
public class RegionRequest {

    @NotNull
    private Region region;
    @NotNull
    @Valid
    private LngLat lngLat;

    public RegionRequest(Region region, LngLat lngLat) {
        this.region = region;
        this.lngLat = lngLat;
    }
    public Region getRegion() {
        return region;
    }
    public void setRegion(Region region) {
        this.region = region;
    }
    public LngLat getPosition() {
        return lngLat;
    }
    public void setPosition(LngLat lngLat) {
        this.lngLat = lngLat;
    }
}
