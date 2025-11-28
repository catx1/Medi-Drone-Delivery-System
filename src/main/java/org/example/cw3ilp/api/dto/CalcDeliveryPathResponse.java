package org.example.cw3ilp.api.dto;

import lombok.Data;
import org.example.cw3ilp.api.model.DronePath;

import java.util.List;

@Data
public class CalcDeliveryPathResponse {
    private Double totalCost;
    private Integer totalMoves;
    private List<DronePath> dronePaths;
}
