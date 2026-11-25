package com.logistics.proyect.group5.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsNumbersInFullName() {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("Juan123")
                .email("juan@example.com")
                .password("secreto123")
                .build();

        assertTrue(hasFullNameViolation(request));
    }

    @Test
    void acceptsNamesWithAccentsAndApostrophes() {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("José María O'Connor")
                .email("jose@example.com")
                .password("secreto123")
                .build();

        assertFalse(hasFullNameViolation(request));
    }

    private boolean hasFullNameViolation(RegisterRequest request) {
        return validator.validate(request).stream()
                .anyMatch(violation -> "fullName".equals(violation.getPropertyPath().toString()));
    }
}
