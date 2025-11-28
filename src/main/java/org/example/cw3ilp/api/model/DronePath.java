package org.example.cw3ilp.api.model;

import lombok.Data;

import java.util.List;
@Data
public class DronePath {
    private String droneId;
    private List<Delivery> deliveries;
}
