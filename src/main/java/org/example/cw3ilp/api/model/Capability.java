package org.example.cw3ilp.api.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class Capability {
    @NotNull
    private Boolean cooling;
    @NotNull
    private Boolean heating;
    @NotNull
    private Double capacity;
    @NotNull
    private Integer maxMoves;
    @NotNull
    private Double costPerMove;
    @NotNull
    private Double costInitial;
    @NotNull
    private Double costFinal;
}