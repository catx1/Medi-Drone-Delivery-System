package org.example.cw3ilp.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.example.cw3ilp.api.model.LngLat;

public class PositionValidator implements ConstraintValidator<ValidPosition, LngLat> {

    // General valid ranges for coordinates

    // Note:
    // Separate concern to if coordinates are valid for edinburgh,
    // to ensure clear separation and better error messages

    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;

    @Override
    public void initialize(ValidPosition constraintAnnotation) {
    }

    @Override
    public boolean isValid(LngLat lngLat, ConstraintValidatorContext context) {
        // @NotNull handles this
        if (lngLat == null) {
            return true;
        }

        Double lng = lngLat.getLng();
        Double lat = lngLat.getLat();

        // Check if lng and lat are not null
        // @NotNull handles this (skips check if true)
        if (lng == null || lat == null) {
            return true;
        }

        // Check if values are valid numbers
        if (!isValidNumber(lng) || !isValidNumber(lat)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                            "Coordinates must be valid numbers (not NaN or Infinity)")
                    .addConstraintViolation();
            return false;
        }

        // -------------------------------------
        // Check general validity of coordinates
        // -------------------------------------

        boolean lngInRange = lng >= MIN_LONGITUDE && lng <= MAX_LONGITUDE;
        boolean latInRange = lat >= MIN_LATITUDE && lat <= MAX_LATITUDE;

        if (!lngInRange || !latInRange) {
            context.disableDefaultConstraintViolation();

            if (!lngInRange && !latInRange) {
                context.buildConstraintViolationWithTemplate(
                                String.format("Longitude must be between %.1f and %.1f, " +
                                                "Latitude must be between %.1f and %.1f",
                                        MIN_LONGITUDE, MAX_LONGITUDE, MIN_LATITUDE, MAX_LATITUDE))
                        .addConstraintViolation();
            } else if (!lngInRange) {
                context.buildConstraintViolationWithTemplate(
                                String.format("Longitude must be between %.1f and %.1f",
                                        MIN_LONGITUDE, MAX_LONGITUDE))
                        .addConstraintViolation();
            } else {
                context.buildConstraintViolationWithTemplate(
                                String.format("Latitude must be between %.1f and %.1f",
                                        MIN_LATITUDE, MAX_LATITUDE))
                        .addConstraintViolation();
            }
            return false;
        }

        // ----------------------------
        // Edinburgh specific validation
        // -----------------------------

        // Longitude negative, latitude positive
        // (round about Edinburgh coords)
        // Eg. west of prime meridian, north of equator
        boolean lngValid = lng < 0;
        boolean latValid = lat > 0;

        if (!lngValid || !latValid) {
            context.disableDefaultConstraintViolation();

            if (!lngValid && !latValid) {
                context.buildConstraintViolationWithTemplate(
                                "Longitude must be negative and latitude must be positive")
                        .addConstraintViolation();
            } else if (!lngValid) {
                context.buildConstraintViolationWithTemplate(
                                "Longitude must be negative")
                        .addConstraintViolation();
            } else {
                context.buildConstraintViolationWithTemplate(
                                "Latitude must be positive")
                        .addConstraintViolation();
            }
            return false;
        }
        return true;
    }

    /**
     * Checks if a number is valid (not NaN or Infinity)
     */
    private boolean isValidNumber(Double value) {
        return value != null && !Double.isNaN(value) && !Double.isInfinite(value);
    }
}