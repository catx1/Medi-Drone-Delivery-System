package org.example.cw3ilp.controllerTests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.cw3ilp.api.controller.RegionController;
import org.example.cw3ilp.api.dto.*;
import org.example.cw3ilp.api.model.LngLat;
import org.example.cw3ilp.api.model.Region;
import org.example.cw3ilp.service.RegionService;
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

import java.util.List;
import java.util.Arrays;

@WebMvcTest(RegionController.class)
public class RegionControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    // Used to convert java objects to JSON strings
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RegionRequest regionRequest;

    @MockitoBean
    private RegionService regionService;

    // -----------------------------------------------
    // TEST endpoint - /isInRegion
    // -----------------------------------------------

    // Valid test cases
    @ParameterizedTest(name = "{3}")
    @CsvSource({
            "-3.1915, 55.9465, true, 'True - center of square'",
            "-3.1918, 55.9468, true, 'True - upper left'",
            "-3.1912, 55.9462, true, 'True - bottom right'",
            "-3.192, 55.9465, true, 'True - edge case (left edge)'",
            "-3.192, 55.946, true, 'True - on vertex (bottom-left corner)'",
            "-3.185, 55.950, false, 'False - far outside'",
            "-3.200, 55.943, false, 'False - outside to left'",
            "-3.1915, 55.940, false, 'False - outside below'"
    })

    @DisplayName("isInRegion with various point positions - should return true ")
    void isInRegion_variousPoints_returnsExpectedResult(double lng, double lat, boolean expectedInside,
                                                        String expectedMessage)
            throws Exception {
        List<LngLat> regionVertices = Arrays.asList(
                new LngLat(-3.192, 55.946),
                new LngLat(-3.191, 55.946),
                new LngLat(-3.191, 55.947),
                new LngLat(-3.192, 55.947),
                new LngLat(-3.192, 55.946)
        );

        Region region = new Region("test_region", regionVertices);
        LngLat testPoint = new LngLat(lng, lat);
        RegionRequest request = new RegionRequest(region, testPoint);

        when(regionService.isInside(anyList(), anyDouble(), anyDouble()))
                .thenReturn(expectedInside);

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(expectedInside)));

    }

    // Edge case - complex polygon
    @ParameterizedTest(name = "{3}")
    @CsvSource({
            "-3.1915, 55.9465, true, 'True'",
            "-3.1905, 55.9465, true, 'True'",
            "-3.1905, 55.9475, false, 'False - in the concave bit'",
            "-3.188, 55.950, false, 'False'"
    })
    @DisplayName("Complex polygon should return expected result")
    void isInRegion_complexPolygon_returnsExpectedResult(double lng, double lat, boolean expectedInside,
                                                         String expectedMessage)
            throws Exception {
        // Concave polygon
        List<LngLat> regionVertices = Arrays.asList(
                new LngLat(-3.192, 55.946),
                new LngLat(-3.190, 55.946),
                new LngLat(-3.190, 55.947),
                new LngLat(-3.191, 55.947),
                new LngLat(-3.191, 55.948),
                new LngLat(-3.192, 55.948),
                new LngLat(-3.192, 55.946)
        );

        Region region = new Region("test_region", regionVertices);
        LngLat testPoint = new LngLat(lng, lat);
        RegionRequest request = new RegionRequest(region, testPoint);

        when(regionService.isInside(anyList(), anyDouble(), anyDouble()))
                .thenReturn(expectedInside);

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(expectedInside)));
    }

    // Edge case - triangle
    @ParameterizedTest(name = "{3}")
    @CsvSource({
            "-3.1915, 55.9463, true, 'True'",
            "-3.1915, 55.946, true, 'True - on edge'",
            "-3.192, 55.946, true, 'True - on vertex'",
            "-3.188, 55.950, false, 'False'"
    })
    @DisplayName("Triangle should return expected result")
    void isInRegion_triangle_returnsExpectedResult(double lng, double lat, boolean expectedInside,
                                                   String expectedMessage)
            throws Exception {
        // Concave polygon
        List<LngLat> regionVertices = Arrays.asList(
                new LngLat(-3.192, 55.946),
                new LngLat(-3.191, 55.946),
                new LngLat(-3.1915, 55.947),
                new LngLat(-3.192, 55.946)
        );

        Region region = new Region("test_region", regionVertices);
        LngLat testPoint = new LngLat(lng, lat);
        RegionRequest request = new RegionRequest(region, testPoint);

        when(regionService.isInside(anyList(), anyDouble(), anyDouble()))
                .thenReturn(expectedInside);

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(expectedInside)));
    }

    // ------------------------------------
    // Invalid test cases (400 bad request)
    // ------------------------------------

    // Test point is null
    @Test
    @DisplayName("Test point is null should return 400 BAD")
    void isInRegion_nullTestPoint_returns400() throws Exception {

        List<LngLat> regionVertices = Arrays.asList(
                new LngLat(-3.192, 55.946),
                new LngLat(-3.191, 55.946),
                new LngLat(-3.191, 55.947),
                new LngLat(-3.192, 55.947),
                new LngLat(-3.1915, 55.9465)
        );

        Region region = new Region("test_region", regionVertices);
        RegionRequest request = new RegionRequest(region, null);

        when(regionService.isInside(anyList(), anyDouble(), anyDouble()))
                .thenThrow(new IllegalArgumentException("Position cannot be null"));

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // Null region
    @Test
    @DisplayName("Region is null should return 400 BAD")
    void isInRegion_nullRegion_returns400() throws Exception {

        List<LngLat> regionVertices = Arrays.asList(
                new LngLat(-3.192, 55.946),
                new LngLat(-3.191, 55.946),
                new LngLat(-3.191, 55.947),
                new LngLat(-3.192, 55.947),
                new LngLat(-3.1915, 55.9465)
        );

        LngLat testPoint = new LngLat(0.5, 0.5);
        RegionRequest request = new RegionRequest(null,testPoint);

        // Mock to throw exception if controller passes it to service
        // But controller should catch before passing
        when(regionService.isInside(anyList(), anyDouble(), anyDouble()))
                .thenThrow(new IllegalArgumentException("Region cannot be null"));

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // Null region vertices
    @Test
    @DisplayName("Region vertices are null should return 400 BAD")
    void isInRegion_nullRegionVertices_returns400() throws Exception {

        LngLat testPoint = new LngLat(-3.192, 55.946);
        Region region = new Region("test_region", null);
        RegionRequest request = new RegionRequest(region,testPoint);

        // Mock to throw exception if controller passes it to service
        // But controller should catch before passing
        when(regionService.isInside(anyList(), anyDouble(), anyDouble()))
                .thenThrow(new IllegalArgumentException("Region cannot be null"));

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // Empty region
    @Test
    @DisplayName("Region vertices empty should return 400 BAD")
    void isInRegion_emptyRegionVertices_returns400() throws Exception {

        List<LngLat> regionVertices = Arrays.asList();

        LngLat testPoint = new LngLat(-3.192, 55.946);
        Region region = new Region("test_region", regionVertices);
        RegionRequest request = new RegionRequest(region,testPoint);

        // Mock to throw exception if controller passes it to service
        // But controller should catch before passing
        when(regionService.isInside(anyList(), anyDouble(), anyDouble()))
                .thenThrow(new IllegalArgumentException("Region cannot be null"));

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }


    // Single point
    @Test
    @DisplayName("Only one point should return 400 BAD")
    void isInRegion_singlePoint_returns400() throws Exception {

        List<LngLat> regionVertices = Arrays.asList(
                new LngLat(-3.192, 55.946)
        );

        LngLat testPoint = new LngLat(0.5, 0.5);
        Region region = new Region("test_region", regionVertices);
        RegionRequest request = new RegionRequest(region,testPoint);

        // Mock to throw exception if controller passes it to service
        // But controller should catch before passing
        when(regionService.isInside(anyList(), anyDouble(), anyDouble()))
                .thenThrow(new IllegalArgumentException("Region cannot be null"));

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // Region with < 3 points
    @Test
    @DisplayName("Too few vertices should return 400 BAD")
    void isInRegion_tooFewVertices_returns400() throws Exception {

        List<LngLat> regionVertices = Arrays.asList(
                new LngLat(-3.192, 55.946),
                new LngLat(-3.191, 55.946)
                // Only 2 points, needs 3 to close
        );

        LngLat testPoint = new LngLat(-3.191, 55.947);
        Region region = new Region("test_region", regionVertices);
        RegionRequest request = new RegionRequest(region,testPoint);

        // Mock to throw exception if controller passes it to service
        // But controller should catch before passing
        when(regionService.isInside(anyList(), anyDouble(), anyDouble()))
                .thenThrow(new IllegalArgumentException("Region cannot be null"));

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // Open polygon
    @Test
    @DisplayName("Open polygon should return 400 BAD")
    void isInRegion_openPolygon_returns400() throws Exception {

        List<LngLat> regionVertices = Arrays.asList(
                new LngLat(-3.192, 55.946),
                new LngLat(-3.191, 55.946),
                new LngLat(-3.191, 55.947),
                new LngLat(-3.192, 55.947)
                // Position(-3.1915, 55.9465) to close the polygon
        );

        LngLat testPoint =  new LngLat(-3.1915, 55.9465);
        Region region = new Region("test_region", regionVertices);
        RegionRequest request = new RegionRequest(region,testPoint);

        // Mock to throw exception if controller passes it to service
        // But controller should catch before passing
        when(regionService.isInside(anyList(), anyDouble(), anyDouble()))
                .thenThrow(new IllegalArgumentException("Region cannot be null"));

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
