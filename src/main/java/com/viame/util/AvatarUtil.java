package com.viame.util;

import com.viame.util.MimoAvatarGenerator.Mood;
import com.viame.util.MimoAvatarGenerator.Shape;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * High-level Avatar Utility for ViaMe Personas (Drivers, Passengers, Admins).
 *
 * <p>Uses the offline native {@link MimoAvatarGenerator} to produce animated SVGs
 * or local API endpoint references for Desktop and Mobile.</p>
 */
public final class AvatarUtil {

    private AvatarUtil() {}

    /**
     * Driver persona: default mood COOL (sunglasses/smirk), circle shape.
     */
    public static String getDriverAvatarSvg(String driverName) {
        return MimoAvatarGenerator.generateSvg(driverName, 128, Shape.CIRCLE, Mood.COOL, true);
    }

    /**
     * Passenger persona: default mood HAPPY (curved eyes/grin), circle shape.
     */
    public static String getPassengerAvatarSvg(String passengerName) {
        return MimoAvatarGenerator.generateSvg(passengerName, 128, Shape.CIRCLE, Mood.HAPPY, true);
    }

    /**
     * Admin persona: default mood CHEEKY, modern squircle shape.
     */
    public static String getAdminAvatarSvg(String adminEmail) {
        return MimoAvatarGenerator.generateSvg(adminEmail, 128, Shape.SQUIRCLE, Mood.CHEEKY, true);
    }

    /**
     * Return base64 Data URI for inline CSS / HTML / WebViews without any network calls.
     */
    public static String getAvatarDataUri(String seed, Mood mood) {
        return MimoAvatarGenerator.generateDataUri(seed, 96, mood);
    }

    /**
     * Return relative URL for ViaMe REST API server (e.g. {@code /api/avatar?seed=...&mood=...}).
     */
    public static String getLocalApiUrl(String seed, Mood mood) {
        if (seed == null || seed.isBlank()) seed = "default";
        String encodedSeed = URLEncoder.encode(seed.trim(), StandardCharsets.UTF_8);
        String moodParam = (mood != null) ? "&mood=" + mood.name().toLowerCase() : "";
        return "/api/avatar?seed=" + encodedSeed + moodParam;
    }
}

