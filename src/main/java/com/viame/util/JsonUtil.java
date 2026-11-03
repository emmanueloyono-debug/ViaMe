package com.viame.util;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Map;

/**
 * Minimal dependency-free JSON serializer for REST responses
 * (records, collections, maps, numbers, booleans, strings, java.time values).
 */
public final class JsonUtil {

    private JsonUtil() {
    }

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
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}

