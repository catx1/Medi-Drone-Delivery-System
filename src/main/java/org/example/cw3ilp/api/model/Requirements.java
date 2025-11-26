package org.example.cw3ilp.api.model;

import lombok.Data;

@Data
public class Requirements {
    private Double capacity;
    private Boolean cooling;
    private Boolean heating;
    private Double maxCost;
}
