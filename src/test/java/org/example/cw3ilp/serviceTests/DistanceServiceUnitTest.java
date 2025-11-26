package org.example.cw3ilp.serviceTests;

import org.example.cw3ilp.api.model.LngLat;
import org.example.cw3ilp.service.DistanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class DistanceServiceUnitTest {

    private DistanceService distanceService;

    // creates a new distance service before every run
    @BeforeEach
    void setUp() {
        distanceService = new DistanceService();
    }

    // --------------------------------------------
    // TEST computeDistance
    // --------------------------------------------

    // Same position
    @Test
    @DisplayName("computeDistance with same position should return 0")
    void computeDistance_samePosition_returnsZero() {
        LngLat pos = new LngLat(-3.192473, 55.946233);
        double distance = distanceService.computeDistance(pos, pos);
        assertEquals(0.0, distance,
                1e-10); // very strict precision, since it should be identical
    }

    // Identical positions
    @Test
    @DisplayName("computeDistance with identical position should return 0")
    void computeDistance_identicalPositions_returnsZero() {
        LngLat p1 = new LngLat(-3.192473, 55.946233);
        LngLat p2 = new LngLat(-3.192473, 55.946233);
        double distance = distanceService.computeDistance(p1, p2);
        assertEquals(0.0, distance,
                1e-10); // very strict precision, since it should be identical
    }


    // Test various known distances
    @ParameterizedTest(name = "{5}")
    @CsvSource({
            "-3.192473, 55.946233, -3.192473, 55.942617, 0.003616,'Vertical distance'",
            "-3.192473, 55.946233, -3.192323, 55.946233, 0.00015, 'Horizontal distance (STEP_DISTANCE)'",
            "-3.190000, 55.945000, -3.189000, 55.945000, 0.001, '1 degree horizontal'",
            "-3.192000, 55.946000, -3.189000, 55.950000, 0.005, 'Triangle'"
    })
    @DisplayName("computeDistance with known distances - should return correct values")
    void computeDistance_knownDistances_returnsCorrectValue(
            double lng1, double lat1, double lng2, double lat2, double expected, String name) {
        LngLat p1 = new LngLat(lng1, lat1);
        LngLat p2 = new LngLat(lng2, lat2);

        double distance = distanceService.computeDistance(p1, p2);

        assertEquals(expected, distance, 1e-6); // lenient precision
    }

    // Check symmetry
    @Test
    @DisplayName("computeDistance should be symmetric")
    void computeDistance_checkSymmetry_returnsSameDistance() {
        LngLat p1 = new LngLat(-3.192473, 55.946233);
        LngLat p2 = new LngLat(-3.192473, 55.942617);

        double distance1 = distanceService.computeDistance(p1, p2);
        double distance2 = distanceService.computeDistance(p2, p1);

        assertEquals(distance1, distance2,
                1e-10); // high precision, should be identical
    }

    // Null inputs
    @Test
    @DisplayName("computeDistance with null position1 should throw IllegalArgumentException")
    void computeDistance_nullPosition1_throwsException() {
        LngLat p2 = new LngLat(-3.192473, 55.942617);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> distanceService.computeDistance(null, p2)
        );

        assertEquals("Position cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("computeDistance with null position2 should throw IllegalArgumentException")
    void computeDistance_nullPosition2_throwsException() {
        LngLat p1 = new LngLat(-3.192473, 55.942617);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> distanceService.computeDistance(p1, null)
        );

        assertEquals("Position cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("computeDistance with two null positions should throw IllegalArgumentException")
    void computeDistance_nullPositions_throwsException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> distanceService.computeDistance(null, null)
        );

        assertEquals("Position cannot be null", exception.getMessage());
    }

    // --------------------------------------------
    // TEST computeCloseness
    // --------------------------------------------

    // Same position
    @Test
    @DisplayName("computeCloseness with same position should return true")
    void computeCloseness_samePosition_returnsTrue() {
        LngLat pos = new LngLat(-3.192473, 55.946233);
        boolean isClose = distanceService.computeCloseness(pos, pos);
        assertTrue(isClose);
    }

    // Test various valid points
    // name = "{5}" should display 'Very close points' as opposed to the coordinates
    @ParameterizedTest(name = "{5}")
    @CsvSource({
            // True
            "-3.192473, 55.946233, -3.192474, 55.946234, true, 'Very close points'",
            "-3.192473, 55.946233, -3.192473, 55.946233, true, 'Same point (identical points)'",
            "-3.192473, 55.946233, -3.192473, 55.946363, true, 'Just below threshold'",

            // False
            "-3.192473, 55.946233, -3.192473, 55.942617, false, 'Far apart'",
            "-3.192473, 55.946233, -3.192473, 55.946384, false, 'Just above threshold'",
            "-3.192473, 55.946233, -3.192273, 55.946233, false, 'Far above threshold'"
    })
    @DisplayName("computeCloseness with various valid positions")
    void computeCloseness_variousPositions_returnsExpectedValue(
            double lng1, double lat1, double lng2, double lat2, boolean expected, String testName) {
        LngLat p1 = new LngLat(lng1, lat1);
        LngLat p2 = new LngLat(lng2, lat2);

        boolean isClose = distanceService.computeCloseness(p1, p2);

        assertEquals(expected, isClose);
    }

    // Check if symmetrical
    @Test
    @DisplayName("computeCloseness should be symmetric")
    void computeCloseness_symmetricPositions_returnsSameResult() {
        LngLat p1 = new LngLat(-3.192473, 55.946233);
        LngLat p2 = new LngLat(-3.192474, 55.946234);

        boolean isClose1 = distanceService.computeCloseness(p1, p2);
        boolean isClose2 = distanceService.computeCloseness(p2, p1);

        assertEquals(isClose1, isClose2);
    }

    // Null input handling
    @Test
    @DisplayName("computeCloseness with null position1 should throw IllegalArgumentException")
    void computeCloseness_nullPosition1_throwsException() {
        LngLat p2 = new LngLat(-3.192473, 55.942617);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> distanceService.computeCloseness(null, p2));

        assertEquals("Position cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("computeCloseness with null position2 should throw IllegalArgumentException")
    void computeCloseness_nullPosition2_throwsException() {
        LngLat p1 = new LngLat(-3.192473, 55.942617);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> distanceService.computeCloseness(p1, null)
        );

        assertEquals("Position cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("computeCloseness with null positions should throw IllegalArgumentException")
    void computeCloseness_nullPositions_throwsException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> distanceService.computeCloseness(null, null)
        );
        assertEquals("Position cannot be null", exception.getMessage());
    }

    // --------------------------------------------
    // TEST computeNextPosition
    // --------------------------------------------

    // Test all 16 angles
    @ParameterizedTest
    @ValueSource(doubles = {0.0, 22.5, 45.0, 67.5, 90.0, 112.5, 135.0, 157.5,
            180.0, 202.5, 225.0, 247.5, 270.0, 292.5, 315.0, 337.5})
    @DisplayName("computeNextPosition with all valid angles should not throw exception")
    void computeNextPosition_validAngles_doesNotThrow(double angle) {
        LngLat start = new LngLat(-3.192473, 55.946233);
        assertDoesNotThrow(() -> distanceService.computeNextPosition(start, angle));
    }

    // Test four main directions
    @Test
    @DisplayName("computeNextPosition at 0° should move East")
    void computeNextPosition_angle0_movesEast() {
        LngLat start = new LngLat(-3.192473, 55.946233);
        LngLat next = distanceService.computeNextPosition(start, 0.0);
        assertEquals(-3.192473 + 0.00015, next.getLng(), 1e-10);
        assertEquals(55.946233, next.getLat(), 1e-10);
    }

    @Test
    @DisplayName("computeNextPosition at 90° should move North")
    void computeNextPosition_angle90_movesNorth() {
        LngLat start = new LngLat(-3.192473, 55.946233);
        LngLat next = distanceService.computeNextPosition(start, 90.0);
        assertEquals(-3.192473, next.getLng(), 1e-10);
        assertEquals(55.946233 + 0.00015, next.getLat(), 1e-10);
    }

    @Test
    @DisplayName("computeNextPosition at 180° should move West")
    void computeNextPosition_angle180_movesWest() {
        LngLat start = new LngLat(-3.192473, 55.946233);

        LngLat next = distanceService.computeNextPosition(start, 180.0);

        assertEquals(-3.192473 - 0.00015, next.getLng(), 1e-10);
        assertEquals(55.946233, next.getLat(), 1e-10);
    }

    @Test
    @DisplayName("computeNextPosition at 270° should move South")
    void computeNextPosition_angle270_movesSouth() {
        LngLat start = new LngLat(-3.192473, 55.946233);

        LngLat next = distanceService.computeNextPosition(start, 270.0);

        assertEquals(-3.192473, next.getLng(), 1e-10);
        assertEquals(55.946233 - 0.00015, next.getLat(), 1e-10);
    }

    // Test a diagonal direction
    @Test
    @DisplayName("computeNextPosition at 45° should move Northeast")
    void computeNextPosition_angle45_movesNortheast() {
        LngLat start = new LngLat(-3.192473, 55.946233);
        LngLat next = distanceService.computeNextPosition(start, 45.0);
        // At 45°, dx and dy should be equal: 0.00015 * cos(45°) ≈ 0.00010607
        assertEquals(-3.192473 + 0.00010607, next.getLng(), 1e-7);
        assertEquals(55.946233 + 0.00010607, next.getLat(), 1e-7);
    }

    // Test a diff start position
    @Test
    @DisplayName("computeNextPosition from different Edinburgh position should calculate correctly")
    void computeNextPosition_differentStart_calculatesCorrectly() {
        LngLat start = new LngLat(-3.184319, 55.944494);
        LngLat next = distanceService.computeNextPosition(start, 0.0);
        assertEquals(-3.184319 + 0.00015, next.getLng(), 1e-10);
        assertEquals(55.944494, next.getLat(), 1e-10);
    }

    // Invalid angle ranges <0 or >= 360
    @ParameterizedTest
    @ValueSource(doubles = {-1.0, -90.0, 360.0, 361.0, 400.0})
    @DisplayName("computeNextPosition with out-of-range angles should throw IllegalArgumentException")
    void computeNextPosition_outOfRangeAngle_throwsException(double angle) {
        LngLat start = new LngLat(-3.192473, 55.946233);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> distanceService.computeNextPosition(start, angle)
        );
        assertTrue(exception.getMessage().contains("Invalid angle"));
        assertTrue(exception.getMessage().contains("Must be between 0 (inclusive) and 360 (exclusive)"));
    }

    // Test invalid angle increments
    @ParameterizedTest
    @ValueSource(doubles = {1.0, 10.0, 23.0, 44.5, 100.0, 200.0, 359.0})
    @DisplayName("computeNextPosition with non-22.5° increment angles should throw IllegalArgumentException")
    void computeNextPosition_nonIncrementAngle_throwsException(double angle) {
        LngLat start = new LngLat(-3.192473, 55.946233);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> distanceService.computeNextPosition(start, angle)
        );
        assertTrue(exception.getMessage().contains("Invalid angle"));
        assertTrue(exception.getMessage().contains("22.5° increments"));
    }

    // Test consecutive moves
    @Test
    @DisplayName("computeNextPosition multiple times should accumulate correctly")
    void computeNextPosition_multipleMoves_accumulatesCorrectly() {
        LngLat start = new LngLat(-3.192473, 55.946233);

        // Move East twice
        LngLat next1 = distanceService.computeNextPosition(start, 0.0);
        LngLat next2 = distanceService.computeNextPosition(next1, 0.0);

        assertEquals(-3.192473 + 0.0003, next2.getLng(), 1e-10);
        assertEquals(55.946233, next2.getLat(), 1e-10);
    }

}
