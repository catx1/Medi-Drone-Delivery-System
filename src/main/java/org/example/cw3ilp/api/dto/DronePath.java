package org.example.cw3ilp.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class DronePath {
    private String droneId;
    private List<Delivery> deliveries;
}
