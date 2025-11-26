package org.example.cw3ilp.api.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.cw3ilp.api.model.LngLat;

@Data
public class NextPositionRequest {

    @NotNull
    private Double angle;
    @NotNull
    @Valid
    private LngLat start;

}
