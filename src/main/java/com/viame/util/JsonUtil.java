package com.viame.util;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal dependency-free JSON serializer and parser for REST requests and responses.
 * Handles records, collections, maps, numbers, booleans, strings, java.time values,
 * nested structures, and character escapes reliably without external libraries.
 */
public final class JsonUtil {

    private JsonUtil() {
    }

    // --- JSON Serialization ---

    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof BigDecimal bd) {
            sb.append(bd.toPlainString());
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof CharSequence || value instanceof Enum<?> || value instanceof TemporalAccessor) {
            sb.append('"').append(escape(value.toString())).append('"');
        } else if (value instanceof Record record) {
            writeRecord(sb, record);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(String.valueOf(e.getKey()))).append("\":");
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof Collection<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                write(sb, item);
            }
            sb.append(']');
        } else {
            sb.append('"').append(escape(value.toString())).append('"');
        }
    }

    private static void writeRecord(StringBuilder sb, Record record) {
        sb.append('{');
        RecordComponent[] components = record.getClass().getRecordComponents();
        for (int i = 0; i < components.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(components[i].getName()).append("\":");
            try {
                write(sb, components[i].getAccessor().invoke(record));
            } catch (ReflectiveOperationException e) {
                sb.append("null");
            }
        }
        sb.append('}');
    }

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format(Locale.US, "\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    // --- JSON Parsing ---

    /**
     * Parses a JSON string into an Object (Map, List, String, Boolean, null).
     */
    public static Object parse(String json) {
        if (json == null || json.isBlank()) return null;
        return new JsonParser(json).parse();
    }

    /**
     * Parses a JSON object string into a generic Map.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object result = parse(json);
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

    /**
     * Robust, dependency-free JSON parser that maps all top-level keys to String representations.
     * Preserves exact numeric representations and serializes nested objects/arrays to JSON strings.
     */
    public static Map<String, String> parseSimpleJson(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        Object parsed = parse(json);
        if (!(parsed instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object val = entry.getValue();
            if (val == null) {
                result.put(key, null);
            } else if (val instanceof String s) {
                result.put(key, s);
            } else if (val instanceof Map<?, ?> || val instanceof List<?>) {
                result.put(key, toJson(val));
            } else {
                result.put(key, String.valueOf(val));
            }
        }
        return result;
    }

    /**
     * Recursive descent JSON parser supporting all standard JSON tokens, nested objects/arrays,
     * primitives, and escaped characters.
     */
    public static final class JsonParser {
        private final String src;
        private final int len;
        private int pos = 0;

        public JsonParser(String src) {
            this.src = src != null ? src : "";
            this.len = this.src.length();
        }

        public Object parse() {
            skipWhitespace();
            if (pos >= len) return null;
            Object val = parseValue();
            skipWhitespace();
            return val;
        }

        private void skipWhitespace() {
            while (pos < len) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private Object parseValue() {
            skipWhitespace();
            if (pos >= len) {
                throw new IllegalArgumentException("Unexpected end of JSON input");
            }
            char c = src.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"' || c == '\'') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            if (c == '-' || Character.isDigit(c)) return parseNumber();
            throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + pos);
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // skip '{'
            skipWhitespace();
            if (pos < len && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (pos < len) {
                skipWhitespace();
                if (pos >= len) break;
                if (src.charAt(pos) == '}') {
                    pos++;
                    break;
                }
                String key;
                if (src.charAt(pos) == '"' || src.charAt(pos) == '\'') {
                    key = parseString();
                } else {
                    int start = pos;
                    while (pos < len && src.charAt(pos) != ':' && !Character.isWhitespace(src.charAt(pos))) {
                        pos++;
                    }
                    key = src.substring(start, pos);
                }
                skipWhitespace();
                if (pos >= len || src.charAt(pos) != ':') {
                    throw new IllegalArgumentException("Expected ':' after key at position " + pos);
                }
                pos++; // skip ':'
                skipWhitespace();
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (pos < len && src.charAt(pos) == ',') {
                    pos++; // skip ','
                } else if (pos < len && src.charAt(pos) == '}') {
                    pos++; // skip '}'
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' in object at position " + pos);
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // skip '['
            skipWhitespace();
            if (pos < len && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (pos < len) {
                skipWhitespace();
                if (pos >= len) break;
                if (src.charAt(pos) == ']') {
                    pos++;
                    break;
                }
                Object val = parseValue();
                list.add(val);
                skipWhitespace();
                if (pos < len && src.charAt(pos) == ',') {
                    pos++; // skip ','
                } else if (pos < len && src.charAt(pos) == ']') {
                    pos++; // skip ']'
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' in array at position " + pos);
                }
            }
            return list;
        }

        private String parseString() {
            char quote = src.charAt(pos);
            pos++; // skip opening quote
            StringBuilder sb = new StringBuilder();
            while (pos < len) {
                char c = src.charAt(pos++);
                if (c == quote) {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= len) throw new IllegalArgumentException("Unterminated escape sequence");
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\'' -> sb.append('\'');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > len) throw new IllegalArgumentException("Invalid unicode escape");
                            String hex = src.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string literal");
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            } else if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid boolean literal at position " + pos);
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid null literal at position " + pos);
        }

        private Object parseNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') pos++;
            while (pos < len && Character.isDigit(src.charAt(pos))) pos++;
            if (pos < len && src.charAt(pos) == '.') {
                pos++;
                while (pos < len && Character.isDigit(src.charAt(pos))) pos++;
            }
            if (pos < len && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                pos++;
                if (pos < len && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
                while (pos < len && Character.isDigit(src.charAt(pos))) pos++;
            }
            return src.substring(start, pos);
        }
    }
}
