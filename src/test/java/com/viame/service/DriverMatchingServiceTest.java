package com.viame.service;

import com.viame.exception.DriverUnavailableException;
import com.viame.model.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DriverMatchingService Integration and Spatial Proximity Tests")
class DriverMatchingServiceTest {

    private DriverMatchingService matchingService;

    @BeforeEach
    void setUp() {
        matchingService = new DriverMatchingService();
    }

    @Test
    @DisplayName("Find candidates around Douala center sorted nearest-first")
    void testFindCandidatesNearAkwa() {
        // Akwa Palace Hotel: 4.051056, 9.767869
        List<DriverMatchingService.DriverCandidate> candidates =
                matchingService.findCandidates(4.051056, 9.767869, 10.0);

        assertNotNull(candidates);
        assertFalse(candidates.isEmpty(), "Should find online drivers in Douala");

        // Verify distance sorting: candidate[i].distance <= candidate[i+1].distance
        for (int i = 0; i < candidates.size() - 1; i++) {
            assertTrue(candidates.get(i).distanceKm() <= candidates.get(i + 1).distanceKm(),
                    "Candidates must be sorted ascending by distance");
        }

        // Verify driver attributes
        Driver firstDriver = candidates.get(0).driver();
        assertNotNull(firstDriver);
        assertTrue(firstDriver.getId() > 0);
    }

    @Test
    @DisplayName("Find nearest driver returns closest online driver")
    void testFindNearest() {
        DriverMatchingService.DriverCandidate nearest = matchingService.findNearest(4.051056, 9.767869);
        assertNotNull(nearest);
        assertTrue(nearest.distanceKm() <= DriverMatchingService.EXTENDED_RADIUS_KM);
    }

    @Test
    @DisplayName("Find nearest driver in remote location throws DriverUnavailableException")
    void testFindNearestInRemoteLocationThrowsException() {
        // Far offshore in the Atlantic Ocean (0.0, 0.0) where no drivers exist
        assertThrows(DriverUnavailableException.class, () ->
                matchingService.findNearest(0.0, 0.0));
    }
}
