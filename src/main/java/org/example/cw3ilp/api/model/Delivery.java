package org.example.cw3ilp.api.model;

import lombok.Data;

import java.util.List;

@Data
public class Delivery {
    private Integer deliveryId;
    private List<LngLatAlt> flightPath;
}
