package org.example.cw3ilp.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;


// custom annotation to ensure positions are valid
@Documented
@Constraint(validatedBy = PositionValidator.class)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPosition {
    String message() default "Invalid position, longitude must be negative and " +
            "latitude must be positive";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}