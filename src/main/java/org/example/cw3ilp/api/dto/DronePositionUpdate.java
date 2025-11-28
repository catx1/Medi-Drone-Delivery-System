package org.example.cw3ilp.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DronePositionUpdate {
    private String droneId;
    private Double lng;
    private Double lat;
    private String status;
    private Integer deliveryId;
    private Double percentComplete;
    private Long timestamp;
}
