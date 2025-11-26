package org.example.cw3ilp.api.model;

import lombok.Data;
import java.util.List;

@Data
public class DronesAvailability {
    private String id;
    private List<Availability> availability;
}
