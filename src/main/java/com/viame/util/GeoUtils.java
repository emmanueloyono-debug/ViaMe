package com.viame.util;

/**
 * Geospatial helpers for distance estimation and driver proximity matching.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    /** Urban roads are not straight lines: road distance ~1.3x great-circle distance. */
    public static final double ROAD_DISTANCE_FACTOR = 1.3;

    /** Average urban speed used to estimate trip duration (Douala traffic). */
    public static final double AVERAGE_URBAN_SPEED_KMH = 25.0;

    private GeoUtils() {
    }

    /** Great-circle distance between two GPS points (Haversine formula), in kilometres. */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /** Estimated driving distance in km (Haversine x road factor). */
    public static double estimateRoadDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        return haversineKm(lat1, lng1, lat2, lng2) * ROAD_DISTANCE_FACTOR;
    }

    /** Estimated driving time in whole minutes (at least 1). */
    public static int estimateDurationMin(double roadDistanceKm) {
        return Math.max(1, (int) Math.ceil(roadDistanceKm / AVERAGE_URBAN_SPEED_KMH * 60.0));
    }

    /** Latitude delta (degrees) covering {@code radiusKm}. */
    public static double latDelta(double radiusKm) {
        return radiusKm / 110.574;
    }

    /** Longitude delta (degrees) covering {@code radiusKm} at the given latitude. */
    public static double lngDelta(double radiusKm, double atLat) {
        double cos = Math.cos(Math.toRadians(atLat));
        return radiusKm / (111.320 * Math.max(cos, 0.01));
    }
}

