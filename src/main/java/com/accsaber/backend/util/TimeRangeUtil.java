package com.accsaber.backend.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public final class TimeRangeUtil {

    private TimeRangeUtil() {
    }

    private static ChronoUnit parseUnit(String unit) {
        return switch (unit.toLowerCase()) {
            case "h" -> ChronoUnit.HOURS;
            case "d" -> ChronoUnit.DAYS;
            case "w" -> ChronoUnit.WEEKS;
            case "mo" -> ChronoUnit.MONTHS;
            default -> throw new IllegalArgumentException(
                    "Invalid time unit: " + unit + ". Use h, d, w, or mo");
        };
    }

    public static Instant computeSince(int amount, String unit) {
        return ZonedDateTime.now(ZoneOffset.UTC).minus(amount, parseUnit(unit)).toInstant();
    }

    public static String granularity(Instant since) {
        long days = ChronoUnit.DAYS.between(since, Instant.now());
        return days > 65 ? "week" : "day";
    }
}
