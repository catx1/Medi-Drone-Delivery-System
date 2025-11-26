package org.example.cw3ilp.serviceTests;

import org.example.cw3ilp.api.model.LngLat;
import org.example.cw3ilp.service.RegionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RegionServiceUnitTest {

    private RegionService regionService;

    @BeforeEach
    void setUp() {
        regionService = new RegionService();
    }

    // Helper methods to create regions
    private List<LngLat> createSquareRegion() {
        return Arrays.asList(
                new LngLat(-3.192, 55.946),
                new LngLat(-3.192, 55.947),
                new LngLat(-3.191, 55.947),
                new LngLat(-3.191, 55.946),
                new LngLat(-3.192, 55.946));
    }

    private List<LngLat> createTriangleRegion() {
        return Arrays.asList(
                new LngLat(-3.192, 55.946),
                new LngLat(-3.191, 55.947),
                new LngLat(-3.190, 55.946),
                new LngLat(-3.192, 55.946));
    }


    // True - point inside square
    @Test
    @DisplayName("Point inside square should return true")
    void isInside_pointInsideSquare_returnsTrue() {
        List<LngLat> square = createSquareRegion();
        double xp = -3.1915;
        double yp = 55.9465;

        boolean result = regionService.isInside(square, xp, yp);
        assertTrue(result);
    }

    // True - point inside triangle
    @Test
    @DisplayName("Point inside triangle should return true")
    void isInside_pointInsideTriangle_returnsTrue() {
        List<LngLat> square = createTriangleRegion();
        double xp = -3.191;
        double yp = 55.9463;

        boolean result = regionService.isInside(square, xp, yp);
        assertTrue(result);
    }

    // False - point outside square
    @Test
    @DisplayName("Point outside square should return false")
    void isInside_pointOutsideSquare_returnsFalse() {
        List<LngLat> square = createSquareRegion();
        double xp = -3.190;
        double yp = 55.948;

        boolean result = regionService.isInside(square, xp, yp);
        assertFalse(result);
    }

    // False - point outside triangle
    @Test
    @DisplayName("Point outside triangle should return false")
    void isInside_pointOutsideTriangle_returnsFalse() {
        List<LngLat> square = createSquareRegion();
        double xp = -3.185;
        double yp = 55.948;

        boolean result = regionService.isInside(square, xp, yp);
        assertFalse(result);
    }

    // Edge case - point on an edge
    @Test
    @DisplayName("isInside with point on edge should return true")
    void isInside_pointOnEdge_returnsTrue() {
        List<LngLat> square = createSquareRegion();
        double xp = -3.192;
        double yp = 55.9465;

        boolean result = regionService.isInside(square, xp, yp);
        assertTrue(result);
    }

    // Edge case - Point on a corner
    @Test
    @DisplayName("isInside with point at vertex should return true")
    void isInside_pointAtVertex_returnsTrue() {
        List<LngLat> square = createSquareRegion();
        double xp = -3.192;
        double yp = 55.946;

        boolean result = regionService.isInside(square, xp, yp);
        assertTrue(result);
    }


    // Test some various points
    @ParameterizedTest(name = "{3}")
    @CsvSource({
            // Inside square
            "-3.1915, 55.9465, true, 'Center of square'",
            "-3.1918, 55.9462, true, 'Near bottom-left corner inside'",

            // Outside square
            "-3.193, 55.946, false, 'Left of square'",
            "-3.190, 55.948, false, 'Above and right of square'",
    })
    @DisplayName("isInside with various positions")
    void isInside_variousPositions_returnsExpectedResult(
            double xp, double yp, boolean expected, String description) {
        List<LngLat> square = createSquareRegion();

        boolean result = regionService.isInside(square, xp, yp);
        assertEquals(expected, result);
    }


    // Test with a complex polygon
    @Test
    @DisplayName("isInside with point in concave notch should return false")
    void isInside_concavePolygon_pointInNotch_returnsFalse() {
        List<LngLat> concavePolygon = Arrays.asList(
                new LngLat(-3.200, 55.940),
                new LngLat(-3.200, 55.950),
                new LngLat(-3.190, 55.950),
                new LngLat(-3.190, 55.945),
                new LngLat(-3.195, 55.945),
                new LngLat(-3.195, 55.940),
                new LngLat(-3.200, 55.940)
        );

        double xp = -3.192;
        double yp = 55.942;

        boolean result = regionService.isInside(concavePolygon, xp, yp);
        assertFalse(result);
    }

    @Test
    @DisplayName("isInside with minimum valid polygon (triangle) should work")
    void isInside_minimumPolygon_works() {
        List<LngLat> triangle = createTriangleRegion();
        assertEquals(4, triangle.size());
        assertDoesNotThrow(() -> regionService.isInside(triangle, -3.191, 55.9463));
    }


    // Ray casting edge cases
    @Test
    @DisplayName("isInside when point horizontally aligned with vertex")
    void isInside_horizontallyAlignedWithVertex_calculatesCorrectly() {
        List<LngLat> square = createSquareRegion();

        // Point at same latitude as a vertex but outside
        double xp = -3.189;  // Outside to the right
        double yp = 55.946;  // Same as bottom edge

        boolean result = regionService.isInside(square, xp, yp);

        assertFalse(result);
    }

    @Test
    @DisplayName("isInside when point vertically aligned with vertex")
    void isInside_verticallyAlignedWithVertex_calculatesCorrectly() {
        List<LngLat> square = createSquareRegion();

        // Point at same longitude as left edge but outside
        double xp = -3.192;  // Same as left edge
        double yp = 55.948;  // Above the square

        boolean result = regionService.isInside(square, xp, yp);

        assertFalse(result);
    }


}
