package com.alulalibre.app.aulalibre.shared.util;

import java.time.LocalTime;

/**
 * Single source of truth for the half-open interval overlap rule used
 * everywhere a schedule/request time range must be checked against another:
 * two ranges overlap iff {@code newStart < existingEnd && newEnd > existingStart}.
 */
public final class TimeRangeUtil {

    private TimeRangeUtil() {
    }

    public static boolean overlaps(LocalTime startA, LocalTime endA, LocalTime startB, LocalTime endB) {
        return startA.isBefore(endB) && endA.isAfter(startB);
    }
}
