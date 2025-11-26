package org.example.cw3ilp.controllerTests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.cw3ilp.api.controller.DistanceController;
import org.example.cw3ilp.api.dto.*;
import org.example.cw3ilp.api.model.LngLat;
import org.example.cw3ilp.service.DistanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.mockito.Mockito.when;



@WebMvcTest(DistanceController.class)
public class DistanceControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    // Used to convert java objects to JSON strings
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DistanceService distanceService;

    @MockitoBean
    private NextPositionRequest nextPositionRequest;

    // -----------------------------------------------
    // TEST endpoint - /distanceTo
    // -----------------------------------------------

    @Test
    // Test it returns expected distance
    void distanceTo_returnsDistance() throws Exception {
        LngLat p1 = new LngLat(-3.192473, 55.946233);
        LngLat p2 = new LngLat(-3.192473, 55.942617);

        // Mock service to return expected distance
        when (distanceService.computeDistance(any(LngLat.class), any(LngLat.class)))
                .thenReturn(0.003616);

        DistanceRequest request = new DistanceRequest();
        request.setPosition1(p1);
        request.setPosition2(p2);

        mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("0.003616"));
    }

    @Test
    // Test if a field is Null
    void distanceTo_returnsDistance_whenPosition1IsNull() throws Exception {
        DistanceRequest request = new DistanceRequest();
        request.setPosition1(null);
        request.setPosition2(new LngLat(-3.192473, 55.942617));

        mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    // Test if a field is missing
    void distanceTo_returnsDistance_whenMissingOneField() throws Exception {
        DistanceRequest request = new DistanceRequest();
        request.setPosition2(new LngLat(-3.192473, 55.942617));

        mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    // Test if both fields are missing
    void distanceTo_returnsDistance_whenMissingBothFields() throws Exception {
        DistanceRequest request = new DistanceRequest();

        mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------
    // TEST endpoint - /isCloseTo
    // -----------------------------------------------

    // Valid test cases
    // TODO add in names
    @ParameterizedTest(name = "{5}")
    @CsvSource({
            "-3.192473, 55.946233, -3.192474, 55.946234, true, 'Very close'",
            "-3.192473, 55.946233, -3.192473, 55.946233, true, 'Same point'",
            "-3.192473, 55.946233, -3.192473, 55.942617, false,'Far apart'",
            "-3.192473, 55.946233, -3.192623, 55.946233, true, 'At threshold ( around 0.00015)'",
            "-3.192473, 55.946233, -3.192650, 55.946233, false, 'Just beyond threshold'"
    })
    @DisplayName("isCloseTo with various pairs should return expected output")
    void isCloseTo_variousDistances_returnsExpectedOutput(double lng1, double lat1, double lng2, double lat2,
                                                          boolean expected, String expectedMessage)
            throws Exception {
        LngLat p1 = new LngLat(lng1, lat1);
        LngLat p2 = new LngLat(lng2, lat2);

        DistanceRequest request = new DistanceRequest();
        request.setPosition1(p1);
        request.setPosition2(p2);

        when(distanceService.computeCloseness(any(LngLat.class), any(LngLat.class)))
                .thenReturn(expected);

        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(expected)));
    }

    // Boundary test cases
    @ParameterizedTest(name = "{5}")
    @CsvSource({
            "-3.192473, 55.946233, -3.19232301, 55.946233, true, 'Just below threshold'",
            "-3.192473, 55.946233, -3.192323,    55.946233, false, 'Exactly at threshold'",
            "-3.192473, 55.946233, -3.19232299, 55.946233, false, 'Just above threshold'"
    })
    @DisplayName("isCloseTo with boundary tests should return expected output")
    void isCloseTo_boundaryTest_returnsExpectedOutput(double lng1, double lat1, double lng2, double lat2,
                                                      boolean expected, String expectedMessage)
            throws Exception {
        LngLat p1 = new LngLat(lng1, lat1);
        LngLat p2 = new LngLat(lng2, lat2);

        DistanceRequest request = new DistanceRequest();
        request.setPosition1(p1);
        request.setPosition2(p2);

        when(distanceService.computeCloseness(any(LngLat.class), any(LngLat.class)))
                .thenReturn(expected);

        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(expected)));
    }

    // Null or invalid data - returns 400 bad request

    @Test
    @DisplayName("Null position1 should return 400")
    void isCloseTo_nullPosition1_returns400() throws Exception {
        DistanceRequest request = new DistanceRequest();
        request.setPosition1(null);
        request.setPosition2(new LngLat(-3.192473, 55.946233));

        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Null position2 should return 400")
    void isCloseTo_nullPosition2_returns400() throws Exception {
        DistanceRequest request = new DistanceRequest();
        request.setPosition1(new LngLat(-3.192473, 55.946233));
        request.setPosition2(null);

        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Both positions null should return 400")
    void isCloseTo_bothPositionsNull_returns400() throws Exception {
        DistanceRequest request = new DistanceRequest();
        request.setPosition1(null);
        request.setPosition2(null);

        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Empty request body should return 400")
    void isCloseTo_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }


    // -----------------------------------------------
    // TEST endpoint - /nextPosition
    // -----------------------------------------------

    @Test
    @DisplayName("Valid input should return 200")
    void nextPosition_withValidInput() throws Exception {
        NextPositionRequest nextPositionRequest = new NextPositionRequest();
        nextPositionRequest.setStart(new LngLat(-3.192473, 55.946233));
        nextPositionRequest.setAngle(45.0);

        LngLat expectedNext = new LngLat(-3.192367, 55.946339);

        when (distanceService.computeNextPosition(any(LngLat.class), anyDouble()))
                .thenReturn(expectedNext);

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nextPositionRequest)))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(expectedNext)));
    }

    @Test
    @DisplayName("Angle is outwith range should return 400")
    void nextPosition_angleExceedsRange_returns400() throws Exception {
        NextPositionRequest request = new NextPositionRequest();
        request.setStart(new LngLat(-3.192473, 55.946233));
        request.setAngle(400.0);

        when(distanceService.computeNextPosition(any(LngLat.class), anyDouble()))
                .thenThrow(new IllegalArgumentException("Invalid angle: 400.0"));

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Angle is not one of 16 allowed angles should return 400")
    void nextPosition_angleIsNotInAllowedAngles_returns400() throws Exception {
        NextPositionRequest request = new NextPositionRequest();
        request.setStart(new LngLat(-3.192473, 55.946233));
        request.setAngle(13.1);

        when(distanceService.computeNextPosition(any(LngLat.class), anyDouble()))
                .thenThrow(new IllegalArgumentException("Angle must be in 22.5° increments"));

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Angle is null should return 400")
    void nextPosition_angleIsNull_returns400() throws Exception {
        NextPositionRequest request = new NextPositionRequest();
        request.setStart(new LngLat(-3.192473, 55.946233));
        request.setAngle(null);

        when(distanceService.computeNextPosition(any(LngLat.class), anyDouble()))
                .thenThrow(new IllegalArgumentException("Angle is null!"));

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Position is null should return 400")
    void nextPosition_positionIsNull_returns400() throws Exception {
        NextPositionRequest request = new NextPositionRequest();
        request.setAngle(45.0);

        when(distanceService.computeNextPosition(any(LngLat.class), anyDouble()))
                .thenThrow(new IllegalArgumentException("Start position is null!"));

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Angle is negative should return 400")
    void nextPosition_angleIsNegative_returns400() throws Exception {
        NextPositionRequest request = new NextPositionRequest();
        request.setStart(new LngLat(-3.192473, 55.946233));
        request.setAngle(-45.0);

        when(distanceService.computeNextPosition(any(LngLat.class), anyDouble()))
                .thenThrow(new IllegalArgumentException("Angle is negative!"));

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }


    // Test accepted boundary angles
    @ParameterizedTest
    @CsvSource({
            "0.0",
            "22.5",
            "337.5"
    })
    @DisplayName("Boundary angles should be accepted")
    void nextPosition_boundaryAngles_accepted(double angle) throws Exception {
        NextPositionRequest request = new NextPositionRequest();
        request.setStart(new LngLat(-3.192473, 55.946233));
        request.setAngle(angle);

        when(distanceService.computeNextPosition(any(LngLat.class), eq(angle)))
                .thenReturn(new LngLat(-3.192473, 55.946383));

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    // Test rejected boundary angles
    @ParameterizedTest
    @CsvSource({
            "-0.1",
            "360.0",
            "360.1"
    })
    @DisplayName("Invalid boundary angles should be rejected")
    void nextPosition_invalidBoundaryAngles_returns400(double angle) throws Exception {
        NextPositionRequest request = new NextPositionRequest();
        request.setStart(new LngLat(-3.192473, 55.946233));
        request.setAngle(angle);

        when(distanceService.computeNextPosition(any(LngLat.class), anyDouble()))
                .thenThrow(new IllegalArgumentException("Invalid angle"));

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

}

