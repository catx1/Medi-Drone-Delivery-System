package org.example.cw3ilp;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.example.cw3ilp.api.model.LngLat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PositionValidatorUnitTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpAll() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDownAll() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    @DisplayName("Valid position should pass validation")
    void validEdinburghPosition_passesValidation() {
        LngLat lngLat = new LngLat(-3.192473, 55.946233);

        Set<ConstraintViolation<LngLat>> violations = validator.validate(lngLat);
        assertTrue(violations.isEmpty());
    }

    // Some various edinburgh locations
    @ParameterizedTest(name = "{2}")
    @CsvSource({
            "-3.192473, 55.946233, 'Central Edinburgh'",
            "-3.184319, 55.944494, 'General Edinburgh location'",
            "-3.200000, 55.950000, 'West Edinburgh'",
            "-0.1, 0.1, 'Edge case, barely negative lng, barely positive lat'",
            "-179.9, 89.9, 'Extreme valid values'"
    })
    @DisplayName("Valid Edinburgh coordinates should pass validation")
    void validCoordinates_passValidation(double lng, double lat, String name) {
        LngLat lngLat = new LngLat(lng, lat);

        Set<ConstraintViolation<LngLat>> violations = validator.validate(lngLat);
        assertTrue(violations.isEmpty());
    }

    @ParameterizedTest(name = "{2}")
    @CsvSource({
            "200.0, 55.944425, 'Longitude > 180'",
            "-200.0, 55.944425, 'Longitude < -180'",
            "-3.188267, 100.0, 'Latitude > 90'",
            "-3.188267, -100.0, 'Latitude < -90'"
    })
    @DisplayName("Coordinates outside valid ranges should be invalid")
    void outOfRange_failsValidation(double lng, double lat, String description) {
        LngLat lngLat = new LngLat(lng, lat);
        Set<ConstraintViolation<LngLat>> violations = validator.validate(lngLat);
        assertFalse(violations.isEmpty(), "Should fail for: " + description);
    }

    @Test
    @DisplayName("Positive longitude, should fail validation")
    void positiveLongitude_failsValidation() {
        LngLat lngLat = new LngLat(3.192473, 55.946233);

        Set<ConstraintViolation<LngLat>> violations = validator.validate(lngLat);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Longitude must be negative")));
    }

    @Test
    @DisplayName("Negative latitude - should fail validation")
    void negativeLatitude_failsValidation() {
        LngLat lngLat = new LngLat(-3.192473, -55.946233);

        Set<ConstraintViolation<LngLat>> violations = validator.validate(lngLat);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Latitude must be positive")));
    }

    @Test
    @DisplayName("Both invalid coordinates should fail validation")
    void bothInvalid_failsValidation() {
        LngLat lngLat = new LngLat(3.192473, -55.946233);

        Set<ConstraintViolation<LngLat>> violations = validator.validate(lngLat);
        assertFalse(violations.isEmpty());
    }

    // Check zero lat and lng
    @Test
    @DisplayName("Zero longitude should fail validation")
    void zeroLongitude_failsValidation() {
        LngLat lngLat = new LngLat(0.0, 55.946233);

        Set<ConstraintViolation<LngLat>> violations = validator.validate(lngLat);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("Zero latitude should fail validation")
    void zeroLatitude_failsValidation() {
        LngLat lngLat = new LngLat(-3.192473, 0.0);

        Set<ConstraintViolation<LngLat>> violations = validator.validate(lngLat);
        assertFalse(violations.isEmpty());
    }

    // Check @NotNull correctly handles values
    @Test
    @DisplayName("Null values should be handled by @NotNull")
    void nullValues_handledByNotNull() {
        LngLat lngLat = new LngLat(null, null);
        Set<ConstraintViolation<LngLat>> violations = validator.validate(lngLat);

        // Should have @NotNull violation
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("must not be null")));
    }


    // Test NaN and infinite values
    @Test
    @DisplayName("NaN longitude should fail validation")
    void nanLongitude_failsValidation() {
        LngLat lngLat = new LngLat(Double.NaN, 55.946233);

        Set<ConstraintViolation<LngLat>> violations = validator.validate(lngLat);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("valid numbers")));
    }

    @Test
    @DisplayName("NaN latitude should fail validation")
    void nanLatitude_failsValidation() {
        LngLat lngLat = new LngLat(-3.192473, Double.NaN);

        Set<ConstraintViolation<LngLat>> violations = validator.validate(lngLat);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("valid numbers")));
    }

    @Test
    @DisplayName("Infinite longitude should fail validation")
    void infiniteLongitude_failsValidation() {
        LngLat lngLat = new LngLat(Double.POSITIVE_INFINITY, 55.946233);

        Set<ConstraintViolation<LngLat>> violations = validator.validate(lngLat);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("valid numbers")));
    }

    @Test
    @DisplayName("Infinite latitude should fail validation")
    void infiniteLatitude_failsValidation() {
        LngLat lngLat = new LngLat(-3.192473, Double.NEGATIVE_INFINITY);

        Set<ConstraintViolation<LngLat>> violations = validator.validate(lngLat);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("valid numbers")));
    }

    // Check some other edge cases
    @ParameterizedTest
    @CsvSource({
            "-180.0, 55.946233, 'Min valid longitude'",
            "-3.192473, 90.0, 'Max valid latitude'",
            "-0.000001, 0.000001, 'Minimum Edinburgh valid values'"
    })
    @DisplayName("Boundary values should pass validation")
    void boundaryValues_passValidation(double lng, double lat, String description) {
        LngLat lngLat = new LngLat(lng, lat);

        Set<ConstraintViolation<LngLat>> violations = validator.validate(lngLat);
        assertTrue(violations.isEmpty(), "Should pass for: " + description);
    }
}