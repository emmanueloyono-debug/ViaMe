package com.viame.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Robust JSON Parser Tests")
class RobustJsonParserTest {

    @Test
    @DisplayName("Parses deeply nested JSON structures without failure")
    void testDeeplyNestedObjects() {
        String json = """
        {
            "service": "ViaMe",
            "metadata": {
                "version": "1.0",
                "settings": {
                    "autoAssign": true,
                    "maxRadiusKm": 15.5
                }
            },
            "metrics": {
                "activeDrivers": 42
            }
        }
        """;

        Map<String, Object> map = JsonUtil.parseObject(json);
        assertEquals("ViaMe", map.get("service"));

        assertTrue(map.get("metadata") instanceof Map<?, ?>);
        Map<?, ?> metadata = (Map<?, ?>) map.get("metadata");
        assertEquals("1.0", metadata.get("version"));

        Map<?, ?> settings = (Map<?, ?>) metadata.get("settings");
        assertEquals(Boolean.TRUE, settings.get("autoAssign"));
        assertEquals("15.5", settings.get("maxRadiusKm"));

        Map<String, String> simpleMap = JsonUtil.parseSimpleJson(json);
        assertEquals("ViaMe", simpleMap.get("service"));
        assertNotNull(simpleMap.get("metadata"));
        assertTrue(simpleMap.get("metadata").contains("autoAssign"));
    }

    @Test
    @DisplayName("Parses JSON arrays containing primitives, strings and objects")
    void testArrayParsing() {
        String json = """
        {
            "tags": ["express", "vip", "airport"],
            "coordinates": [4.051056, 9.767869],
            "stops": [
                {"order": 1, "name": "Akwa Palace"},
                {"order": 2, "name": "Bonanjo"}
            ]
        }
        """;

        Map<String, Object> map = JsonUtil.parseObject(json);
        assertTrue(map.get("tags") instanceof List<?>);
        List<?> tags = (List<?>) map.get("tags");
        assertEquals(3, tags.size());
        assertEquals("express", tags.get(0));

        List<?> coords = (List<?>) map.get("coordinates");
        assertEquals(2, coords.size());
        assertEquals("4.051056", coords.get(0));

        List<?> stops = (List<?>) map.get("stops");
        assertEquals(2, stops.size());
        assertTrue(stops.get(0) instanceof Map<?, ?>);

        Map<String, String> simpleMap = JsonUtil.parseSimpleJson(json);
        assertTrue(simpleMap.get("tags").contains("vip"));
        assertTrue(simpleMap.get("stops").contains("Bonanjo"));
    }

    @Test
    @DisplayName("Parses all JSON primitives: strings, numbers, booleans, and null")
    void testPrimitivesParsing() {
        String json = """
        {
            "name": "Alice Mengue",
            "age": 28,
            "rating": 4.95,
            "discount": -500.00,
            "scientific": 1.25e3,
            "verified": true,
            "suspended": false,
            "notes": null
        }
        """;

        Map<String, String> map = JsonUtil.parseSimpleJson(json);
        assertEquals("Alice Mengue", map.get("name"));
        assertEquals("28", map.get("age"));
        assertEquals("4.95", map.get("rating"));
        assertEquals("-500.00", map.get("discount"));
        assertEquals("1.25e3", map.get("scientific"));
        assertEquals("true", map.get("verified"));
        assertEquals("false", map.get("suspended"));
        assertNull(map.get("notes"));
    }

    @Test
    @DisplayName("Safely handles escape characters including unicode, newlines, tabs, and quotes")
    void testEscapedCharacters() {
        String json = "{\"message\":\"Line 1\\nLine 2\\tTabbed \\\"Quotes\\\" \\\\Backslash \\u0041wesome\"}";
        Map<String, String> map = JsonUtil.parseSimpleJson(json);
        assertEquals("Line 1\nLine 2\tTabbed \"Quotes\" \\Backslash Awesome", map.get("message"));
    }

    @Test
    @DisplayName("Gracefully handles empty and null inputs")
    void testEmptyAndNullInputs() {
        assertTrue(JsonUtil.parseSimpleJson(null).isEmpty());
        assertTrue(JsonUtil.parseSimpleJson("").isEmpty());
        assertTrue(JsonUtil.parseSimpleJson("   ").isEmpty());
        assertTrue(JsonUtil.parseSimpleJson("{}").isEmpty());
    }
}
