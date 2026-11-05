package com.viame.util;

import com.viame.util.MimoAvatarGenerator.Mood;
import com.viame.util.MimoAvatarGenerator.Shape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Mimo Native Offline Avatar Generator Test")
class MimoAvatarGeneratorTest {

    @Test
    @DisplayName("Deterministic: Same seed must produce identical SVG string")
    void testDeterministicOutput() {
        String svg1 = MimoAvatarGenerator.generateSvg("driver.samuel@viame.cm");
        String svg2 = MimoAvatarGenerator.generateSvg("driver.samuel@viame.cm");
        String svgOther = MimoAvatarGenerator.generateSvg("passenger.alice@viame.cm");

        assertNotNull(svg1);
        assertEquals(svg1, svg2, "Identical seed must produce byte-for-byte identical SVG");
        assertNotEquals(svg1, svgOther, "Different seeds should produce different avatar faces/colors");
    }

    @Test
    @DisplayName("SVG Structure: contains valid XML, CSS animations, and clip paths")
    void testSvgStructure() {
        String svg = MimoAvatarGenerator.generateSvg("test-user", 96, Shape.CIRCLE, Mood.HAPPY, true);

        assertTrue(svg.startsWith("<svg"), "Must start with <svg root element");
        assertTrue(svg.endsWith("</svg>"), "Must end with </svg>");
        assertTrue(svg.contains("xmlns=\"http://www.w3.org/2000/svg\""));
        assertTrue(svg.contains("viewBox=\"0 0 100 100\""));
        assertTrue(svg.contains("width=\"96\""));
        assertTrue(svg.contains("height=\"96\""));
        assertTrue(svg.contains("@keyframes mimo-blink"), "Animated SVG must contain CSS keyframe animations");
        assertTrue(svg.contains("@keyframes mimo-float"));
        assertTrue(svg.contains("<foreignObject"), "Must contain foreignObject for layout");
        assertTrue(svg.contains("<clipPath"));
        assertTrue(svg.contains("<radialGradient"));
    }

    @Test
    @DisplayName("All Moods: test generation for all 5 expressions")
    void testAllMoods() {
        for (Mood mood : Mood.values()) {
            String svg = MimoAvatarGenerator.generateSvg("seed-mood", 128, Shape.CIRCLE, mood, true);
            assertNotNull(svg);
            assertTrue(svg.length() > 500, "Generated SVG for mood " + mood + " must not be empty");
        }
    }

    @Test
    @DisplayName("All Shapes: test circle, squircle, square")
    void testAllShapes() {
        String circle = MimoAvatarGenerator.generateSvg("shape-test", 100, Shape.CIRCLE, null, false);
        String squircle = MimoAvatarGenerator.generateSvg("shape-test", 100, Shape.SQUIRCLE, null, false);
        String square = MimoAvatarGenerator.generateSvg("shape-test", 100, Shape.SQUARE, null, false);

        assertTrue(circle.contains("<circle cx=\"50\" cy=\"50\" r=\"50\""));
        assertTrue(squircle.contains("M 0 44 C 0 5.86"));
        assertTrue(square.contains("<rect x=\"0\" y=\"0\" width=\"100\" height=\"100\""));
    }

    @Test
    @DisplayName("Data URI generation produces valid base64 data URI")
    void testDataUri() {
        String dataUri = MimoAvatarGenerator.generateDataUri("alice", 80, Mood.HAPPY);
        assertNotNull(dataUri);
        assertTrue(dataUri.startsWith("data:image/svg+xml;base64,"));
        assertTrue(dataUri.length() > 100);
    }

    @Test
    @DisplayName("AvatarUtil persona helpers produce valid SVGs and URLs")
    void testAvatarUtil() {
        String driverSvg = AvatarUtil.getDriverAvatarSvg("Samuel Eto'o");
        String passengerSvg = AvatarUtil.getPassengerAvatarSvg("Alice Ngo");
        String adminSvg = AvatarUtil.getAdminAvatarSvg("admin@viame.cm");

        assertNotNull(driverSvg);
        assertNotNull(passengerSvg);
        assertNotNull(adminSvg);

        String localUrl = AvatarUtil.getLocalApiUrl("Samuel Eto'o", Mood.COOL);
        assertEquals("/api/avatar?seed=Samuel+Eto%27o&mood=cool", localUrl);
    }

    @Test
    @DisplayName("Null and blank seeds are handled gracefully without exceptions")
    void testNullAndBlankSeed() {
        assertDoesNotThrow(() -> MimoAvatarGenerator.generateSvg(null));
        assertDoesNotThrow(() -> MimoAvatarGenerator.generateSvg("   "));
        assertDoesNotThrow(() -> AvatarUtil.getDriverAvatarSvg(null));
        assertDoesNotThrow(() -> AvatarUtil.getLocalApiUrl(null, null));
    }
}

