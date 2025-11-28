package org.example.cw3ilp.api.model;

import lombok.Data;
import java.util.List;

@Data
public class DronesAvailability {
    private String id;
    private List<Availability> availability;

    @Data
    public static class ServicePoint {
        private String name;
        private LngLatAlt location;
        private Integer id;
    }
}
