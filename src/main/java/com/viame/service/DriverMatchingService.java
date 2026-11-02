package com.viame.service;

import com.viame.dao.DriverDAO;
import com.viame.dao.impl.DriverDAOImpl;
import com.viame.exception.DriverUnavailableException;
import com.viame.model.Driver;
import com.viame.util.GeoUtils;

import java.util.Comparator;
import java.util.List;

/**
 * Proximity dispatch (UC04): finds approved, online drivers near the pickup point.
 *
 * <p>A bounding-box SQL query pre-filters candidates cheaply (uses indexes), then the exact
 * Haversine distance is computed in Java and results are sorted nearest-first.</p>
 */
public class DriverMatchingService {

    public static final double DEFAULT_RADIUS_KM = 5.0;
    public static final double EXTENDED_RADIUS_KM = 10.0;

    public record DriverCandidate(Driver driver, double distanceKm) {
    }

    private final DriverDAO driverDAO;

    public DriverMatchingService() {
        this(new DriverDAOImpl());
    }

    public DriverMatchingService(DriverDAO driverDAO) {
        this.driverDAO = driverDAO;
    }

    public List<DriverCandidate> findCandidates(double lat, double lng, double radiusKm) {
        double dLat = GeoUtils.latDelta(radiusKm);
        double dLng = GeoUtils.lngDelta(radiusKm, lat);
        return driverDAO.findAvailableInBoundingBox(lat - dLat, lat + dLat, lng - dLng, lng + dLng).stream()
                .map(d -> new DriverCandidate(d, GeoUtils.haversineKm(lat, lng, d.getCurrentLat(), d.getCurrentLng())))
                .filter(c -> c.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(DriverCandidate::distanceKm))
                .toList();
    }

    /** Searches within 5 km, widening to 10 km if nobody is found (UC04 alternative flow). */
    public List<DriverCandidate> findCandidatesWithFallback(double lat, double lng) {
        List<DriverCandidate> candidates = findCandidates(lat, lng, DEFAULT_RADIUS_KM);
        return candidates.isEmpty() ? findCandidates(lat, lng, EXTENDED_RADIUS_KM) : candidates;
    }

    public DriverCandidate findNearest(double lat, double lng) {
        return findCandidatesWithFallback(lat, lng).stream().findFirst()
                .orElseThrow(() -> new DriverUnavailableException(
                        "No driver available within " + (int) EXTENDED_RADIUS_KM + " km. Please try again shortly."));
    }
}

