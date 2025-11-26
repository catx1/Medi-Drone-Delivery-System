package org.example.cw3ilp;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.cw3ilp.api.dto.DistanceRequest;
import org.example.cw3ilp.api.dto.NextPositionRequest;
import org.example.cw3ilp.api.dto.RegionRequest;
import org.example.cw3ilp.api.model.LngLat;
import org.example.cw3ilp.api.model.Region;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
public class IntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("/distanceTo returns correct distance")
    void distanceTo_integration_returnsCorrectDistance() throws Exception {
        LngLat p1 = new LngLat(-3.192473, 55.946233);
        LngLat p2 = new LngLat(-3.192473, 55.942617);

        DistanceRequest request = new DistanceRequest();
        request.setPosition1(p1);
        request.setPosition2(p2);

        mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andDo(result -> {
                    String responseContent = result.getResponse().getContentAsString();
                    double actual = Double.parseDouble(responseContent);
                    assertEquals(0.003616, actual, 1e-6);
                });
    }

    @Test
    @DisplayName("distanceTo with null position returns 400")
    void distanceTo_integration_nullPosition_returns400() throws Exception {
        DistanceRequest request = new DistanceRequest();
        request.setPosition1(null);
        request.setPosition2(new LngLat(-3.192473, 55.942617));

        mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("/isCloseTo with close positions returns true")
    void isCloseTo_integration_closePositions_returnsTrue() throws Exception {
        LngLat p1 = new LngLat(-3.192473, 55.946233);
        LngLat p2 = new LngLat(-3.192474, 55.946234);

        DistanceRequest request = new DistanceRequest();
        request.setPosition1(p1);
        request.setPosition2(p2);

        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("isCloseTo with far positions returns false")
    void isCloseTo_integration_farPositions_returnsFalse() throws Exception {
        LngLat p1 = new LngLat(-3.192473, 55.946233);
        LngLat p2 = new LngLat(-3.192473, 55.942617);

        DistanceRequest request = new DistanceRequest();
        request.setPosition1(p1);
        request.setPosition2(p2);

        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    @DisplayName("/nextPosition returns correct next position")
    void nextPosition_integration_returnsCorrectPosition() throws Exception {
        LngLat start = new LngLat(-3.192473, 55.946233);

        NextPositionRequest request = new NextPositionRequest();
        request.setStart(start);
        request.setAngle(0.0);  // Move East

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andDo(result -> {
                    String responseContent = result.getResponse().getContentAsString();
                    LngLat resultLngLat = objectMapper.readValue(responseContent, LngLat.class);

                    assertEquals(-3.192473 + 0.00015, resultLngLat.getLng(), 1e-10);
                    assertEquals(55.946233, resultLngLat.getLat(), 1e-10);
                });
    }

    @Test
    @DisplayName("/nextPosition with invalid angle returns 400")
    void nextPosition_integration_invalidAngle_returns400() throws Exception {
        LngLat start = new LngLat(-3.192473, 55.946233);

        NextPositionRequest request = new NextPositionRequest();
        request.setStart(start);
        request.setAngle(15.0);  // not multiple of 22.5

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // UserIDController Integration Test

    @Test
    @DisplayName("/uid returns correct user ID")
    void getUid_integration_returnsCorrectUid() throws Exception {
        mockMvc.perform(get("/api/v1/uid"))
                .andExpect(status().isOk())
                .andExpect(content().string("s2524237"));
    }

    // RegionController Integration Tests

    @Test
    @DisplayName("/isInRegion with point inside returns true")
    void isInRegion_integration_pointInside_returnsTrue() throws Exception {
        List<LngLat> vertices = Arrays.asList(
                new LngLat(-3.192, 55.946),
                new LngLat(-3.192, 55.947),
                new LngLat(-3.191, 55.947),
                new LngLat(-3.191, 55.946),
                new LngLat(-3.192, 55.946)
        );

        Region region = new Region("TestSquare", vertices);
        LngLat point = new LngLat(-3.1915, 55.9465);

        RegionRequest request = new RegionRequest(region,point);

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("/isInRegion with point outside returns false")
    void isInRegion_integration_pointOutside_returnsFalse() throws Exception {
        // Create a square region
        List<LngLat> vertices = Arrays.asList(
                new LngLat(-3.192, 55.946),
                new LngLat(-3.192, 55.947),
                new LngLat(-3.191, 55.947),
                new LngLat(-3.191, 55.946),
                new LngLat(-3.192, 55.946)  // Closed
        );

        Region region = new Region("TestSquare", vertices);
        LngLat point = new LngLat(-3.190, 55.948);  // Outside

        RegionRequest request = new RegionRequest(region, point);

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    @DisplayName("/isInRegion with open polygon returns 400")
    void isInRegion_integration_openPolygon_returns400() throws Exception {
        // Create an open polygon (first != last)
        List<LngLat> vertices = Arrays.asList(
                new LngLat(-3.192, 55.946),
                new LngLat(-3.192, 55.947),
                new LngLat(-3.191, 55.947),
                new LngLat(-3.191, 55.946)
                // Missing closing vertex
        );

        Region region = new Region("OpenPolygon", vertices);
        LngLat point = new LngLat(-3.1915, 55.9465);

        RegionRequest request = new RegionRequest(region, point);

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("/isInRegion with too few vertices returns 400")
    void isInRegion_integration_tooFewVertices_returns400() throws Exception {
        // Only 3 vertices (minimum is 4 for a closed triangle)
        List<LngLat> vertices = Arrays.asList(
                new LngLat(-3.192, 55.946),
                new LngLat(-3.192, 55.947),
                new LngLat(-3.191, 55.947)
        );

        Region region = new Region("TooSmall", vertices);
        LngLat point = new LngLat(-3.1915, 55.9465);

        RegionRequest request = new RegionRequest(region, point);

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}




