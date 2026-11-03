package com.viame.util;

import com.viame.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InputValidator Regex & Normalization Tests")
class InputValidatorTest {

    @Test
    @DisplayName("Validates email syntax properly")
    void testEmailValidation() {
        assertTrue(InputValidator.isValidEmail("passenger.alice@viame.cm"));
        assertTrue(InputValidator.isValidEmail("admin@saintjeaningenieur.org"));
        assertFalse(InputValidator.isValidEmail("invalid-email"));
        assertFalse(InputValidator.isValidEmail("@viame.cm"));
        assertFalse(InputValidator.isValidEmail(null));
    }

    @Test
    @DisplayName("Validates and normalizes Cameroonian phone numbers")
    void testPhoneValidationAndNormalization() {
        assertTrue(InputValidator.isValidPhone("+237670000001"));
        assertTrue(InputValidator.isValidPhone("670 00 00 01"));
        assertEquals("+237670000001", InputValidator.normalizePhone("670 00 00 01"));
        assertFalse(InputValidator.isValidPhone("12345"));
    }

    @Test
    @DisplayName("Validates vehicle license plates and driver licences")
    void testPlateAndLicenceValidation() {
        assertTrue(InputValidator.isValidPlate("LT 452 AB"));
        assertTrue(InputValidator.isValidPlate("ce 918 cz"));
        assertEquals("CE 918 CZ", InputValidator.normalizePlate("  ce   918 cz "));
        assertFalse(InputValidator.isValidPlate("INVALID_PLATE_99999"));

        assertTrue(InputValidator.isValidLicence("CMR-LT-2023-8891"));
        assertFalse(InputValidator.isValidLicence("12"));
    }

    @Test
    @DisplayName("Validates password strength rules")
    void testPasswordStrength() {
        assertTrue(InputValidator.isStrongPassword("Password123!"));
        assertFalse(InputValidator.isStrongPassword("short1"));
        assertFalse(InputValidator.isStrongPassword("onlylettersHere"));
        assertFalse(InputValidator.isStrongPassword("1234567890"));
    }

    @Test
    @DisplayName("requireNonBlank throws ValidationException on blank input")
    void testRequireNonBlank() {
        assertEquals("Alice", InputValidator.requireNonBlank("  Alice  ", "Name"));
        assertThrows(ValidationException.class, () -> InputValidator.requireNonBlank("   ", "Name"));
    }
}

