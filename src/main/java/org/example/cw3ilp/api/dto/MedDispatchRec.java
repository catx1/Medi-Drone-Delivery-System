package org.example.cw3ilp.api.dto;

import lombok.Data;
import org.example.cw3ilp.api.model.LngLatAlt;
import org.example.cw3ilp.api.model.Requirements;

@Data
public class MedDispatchRec {

    private String date;
    private String time;
    private LngLatAlt delivery;
    private Integer id;
    private Requirements requirements;

}

