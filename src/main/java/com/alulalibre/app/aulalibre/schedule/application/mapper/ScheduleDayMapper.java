package com.alulalibre.app.aulalibre.schedule.application.mapper;

import com.alulalibre.app.aulalibre.shared.exception.ErrorCode;
import com.alulalibre.app.aulalibre.shared.exception.ValidationException;
import java.time.DayOfWeek;
import java.util.Map;

/**
 * {@code java.time.DayOfWeek} is a JDK enum and can't carry a
 * {@code @JsonValue}/{@code @JsonCreator} pair like the domain enums do, so
 * the Spanish day-key mapping required by BACKEND_API_CONTRACT.md §5
 * ({@code DAY_KEYS = ["domingo","lunes",...,"sabado"]}, no accents) lives
 * here instead, as the single place ScheduleMapper/ScheduleService use it.
 */
public final class ScheduleDayMapper {

    private static final Map<DayOfWeek, String> TO_KEY = Map.of(
            DayOfWeek.SUNDAY, "domingo",
            DayOfWeek.MONDAY, "lunes",
            DayOfWeek.TUESDAY, "martes",
            DayOfWeek.WEDNESDAY, "miercoles",
            DayOfWeek.THURSDAY, "jueves",
            DayOfWeek.FRIDAY, "viernes",
            DayOfWeek.SATURDAY, "sabado");

    private static final Map<String, DayOfWeek> FROM_KEY = TO_KEY.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    private ScheduleDayMapper() {
    }

    public static String toKey(DayOfWeek dayOfWeek) {
        return TO_KEY.get(dayOfWeek);
    }

    public static DayOfWeek fromKey(String key) {
        DayOfWeek dayOfWeek = key == null ? null : FROM_KEY.get(key.toLowerCase());
        if (dayOfWeek == null) {
            throw new ValidationException(ErrorCode.INVALID_REQUEST, "Día de la semana inválido: " + key);
        }
        return dayOfWeek;
    }
}
