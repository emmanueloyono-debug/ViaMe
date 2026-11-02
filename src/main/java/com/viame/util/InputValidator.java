package com.viame.util;

import com.viame.exception.ValidationException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Centralised input validation rules (email, Cameroonian phone, plate, licence, password,
 * GPS coordinates). Methods either return a boolean or throw {@link ValidationException}
 * with a user-friendly message.
 */
public final class InputValidator {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    /** Cameroon mobile numbers: optional +237 prefix, then 6 followed by 8 digits. */
    private static final Pattern PHONE = Pattern.compile("^(\\+237)?6\\d{8}$");
    /** e.g. "LT 452 AB", "CE 918 CZ". */
    private static final Pattern PLATE = Pattern.compile("^[A-Z]{2} ?\\d{3,4} ?[A-Z]{1,2}$");
    /** e.g. "CMR-LT-2023-8891". */
    private static final Pattern LICENCE = Pattern.compile("^[A-Z0-9-]{6,40}$");

    public static final int MIN_PASSWORD_LENGTH = 8;

    private InputValidator() {
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.length() <= 150 && EMAIL.matcher(email).matches();
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE.matcher(normalizePhone(phone)).matches();
    }

    public static boolean isValidPlate(String plate) {
        return plate != null && PLATE.matcher(plate.trim().toUpperCase(Locale.ROOT)).matches();
    }

    public static boolean isValidLicence(String licence) {
        return licence != null && LICENCE.matcher(licence.trim().toUpperCase(Locale.ROOT)).matches();
    }

    /** At least 8 characters with at least one letter and one digit. */
    public static boolean isStrongPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) return false;
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        return hasLetter && hasDigit;
    }

    public static boolean isValidCoordinate(double lat, double lng) {
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
                && !Double.isNaN(lat) && !Double.isNaN(lng);
    }

    /** Removes spaces/dashes and normalises to the stored "+2376XXXXXXXX" form when possible. */
    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        String compact = phone.replaceAll("[\\s-]", "");
        if (compact.matches("^6\\d{8}$")) {
            return "+237" + compact;
        }
        if (compact.matches("^2376\\d{8}$")) {
            return "+" + compact;
        }
        return compact;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizePlate(String plate) {
        return plate == null ? null : plate.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " is required");
        }
        return value.trim();
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new ValidationException(message);
        }
    }
}

