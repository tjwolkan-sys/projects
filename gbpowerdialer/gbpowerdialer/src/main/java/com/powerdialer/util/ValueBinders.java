package com.powerdialer.util;

import org.json.JSONObject;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

final class ValueBinders {
    private static final ZoneId LOCAL = ZoneId.systemDefault();

    private static final DateTimeFormatter TS      = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TS_MILL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    static String safeIdentifier(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return s;
        return s.matches("[A-Za-z0-9_\\.\\[\\]]+") ? s : null;
    }

    static Object coerceParam(Object v, String type) {
        if (v == null || JSONObject.NULL.equals(v)) return null;

        if ("boolean".equals(type) || v instanceof Boolean) {
            return toBoolean(v) ? 1 : 0;
        }
        if ("date".equals(type) || "datetime".equals(type) || "timestamp".equals(type)) {
            Date d = coerceDate(v);
            return (d != null) ? new Timestamp(d.getTime()) : null;
        }
        if (v instanceof Number) return v;

        String s = String.valueOf(v).trim();
        if (s.matches("-?\\d+")) {
            try { return Long.valueOf(s); } catch (NumberFormatException ignore) {}
        } else if (s.matches("-?\\d+\\.\\d+")) {
            try { return Double.valueOf(s); } catch (NumberFormatException ignore) {}
        }
        return s;
    }

    private static boolean toBoolean(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        return "1".equals(s) || "true".equals(s) || "yes".equals(s) || "y".equals(s);
    }

    private static Date coerceDate(Object v) {
        if (v == null || JSONObject.NULL.equals(v)) return null;
        if (v instanceof Number) return new Date(((Number) v).longValue());

        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;

        try {
            if (s.length() == 10) {
                LocalDate ld = LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
                return Date.from(ld.atStartOfDay(LOCAL).toInstant());
            }
        } catch (DateTimeParseException ignore) {}

        try {
            LocalDateTime ldt = LocalDateTime.parse(s, TS_MILL);
            return Date.from(ldt.atZone(LOCAL).toInstant());
        } catch (DateTimeParseException ignore) {}

        try {
            LocalDateTime ldt = LocalDateTime.parse(s, TS);
            return Date.from(ldt.atZone(LOCAL).toInstant());
        } catch (DateTimeParseException ignore) {}

        try { return Date.from(Instant.parse(s)); }
        catch (DateTimeParseException ignore) {
            try { return Date.from(OffsetDateTime.parse(s).toInstant()); }
            catch (DateTimeParseException ignore2) {}
        }
        return null;
    }
}
