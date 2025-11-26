package org.example.cw3ilp.api.dto;

import lombok.Data;

@Data
public class QueryCriteriaRequest {
    private String attribute;
    private String operator;
    private String value;
}
