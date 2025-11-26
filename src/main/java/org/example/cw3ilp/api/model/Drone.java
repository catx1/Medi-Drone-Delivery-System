package org.example.cw3ilp.api.model;

import lombok.Data;

@Data
public class Drone {
    private String id;
    private String name;
    private Capability capability;
}