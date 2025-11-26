package org.example.cw3ilp.api.model;

import lombok.Data;
import java.time.LocalTime;
import java.time.DayOfWeek;

@Data
public class Availability {
    private DayOfWeek dayOfWeek;
    private LocalTime from;
    private LocalTime until;
}
