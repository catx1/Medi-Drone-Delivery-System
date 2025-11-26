package org.example.cw3ilp.api.dto;

import lombok.Data;
import org.example.cw3ilp.api.model.LngLatAlt;

@Data
public class ServicePoint {
    private String name;
    private LngLatAlt location;
    private Integer id;
}
