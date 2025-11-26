package org.example.cw3ilp.api.model;

import lombok.Data;

import java.util.List;

@Data
public class RestrictedArea {
    private String name;
    private Integer id;
    private AltitudeLimits limits;
    private List<LngLatAlt> vertices;

}
