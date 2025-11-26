package org.example.cw3ilp.api.dto;

import lombok.Data;
import org.example.cw3ilp.api.model.LngLatAlt;

import java.util.List;

@Data
public class Delivery {
    private Integer deliveryId;
    private List<LngLatAlt> flightPath;
}
