package org.example.cw3ilp.api.model;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class Region {

    @NotNull
    private String name;
    @NotNull
    private final List<LngLat> vertices;

    public Region(String name, List<LngLat> vertices) {
        this.name = name;
        this.vertices = vertices;
    }
}
