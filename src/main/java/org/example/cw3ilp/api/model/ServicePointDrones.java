package org.example.cw3ilp.api.model;

import lombok.Data;

import java.util.List;

@Data
public class ServicePointDrones {
    private Integer servicePointId;
    private List<DronesAvailability> drones;
}
