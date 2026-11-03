package com.viame.util;

import com.viame.model.report.ReportRows.PeakHourRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("JsonUtil Serialization Tests")
public class JsonUtilTest {

    @Test
    @DisplayName("Serializes records, lists, maps, dates and escapes strings")
    public void testSerialization() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", LocalDate.of(2026, 10, 1));
        body.put("count", 1);
        body.put("note", "say \"hi\"\n");
        body.put("rows", List.of(new PeakHourRow(8, 12, 10, new BigDecimal("1.40"))));
        body.put("missing", null);

        assertEquals("{\"from\":\"2026-10-01\",\"count\":1,\"note\":\"say \\\"hi\\\"\\n\","
                + "\"rows\":[{\"hourOfDay\":8,\"totalRequests\":12,\"completed\":10,\"averageSurge\":1.40}],"
                + "\"missing\":null}", JsonUtil.toJson(body));
    }
}

