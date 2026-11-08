package net.omni.extraction.util;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {

    private static final Pattern TIME_PATTERN =
            Pattern.compile("^(\\d+)\\s*(s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours)?$");

    private static final Map<String, Long> TIME_UNITS = Map.ofEntries(
            Map.entry("s", 1L), Map.entry("sec", 1L), Map.entry("secs", 1L),
            Map.entry("second", 1L), Map.entry("seconds", 1L),
            Map.entry("m", 60L), Map.entry("min", 60L), Map.entry("mins", 60L),
            Map.entry("minute", 60L), Map.entry("minutes", 60L),
            Map.entry("h", 3600L), Map.entry("hr", 3600L), Map.entry("hrs", 3600L),
            Map.entry("hour", 3600L), Map.entry("hours", 3600L));

    private TimeUtil() {
    }

    public static Integer parseInt(String raw) {
        if (raw == null)
            return null;

        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Integer parseSeconds(String raw) {
        if (raw == null)
            return null;

        Matcher matcher = TIME_PATTERN.matcher(raw.trim().toLowerCase(Locale.ROOT));

        if (!matcher.matches())
            return null;

        try {
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            long multiplier = unit == null ? 1L : TIME_UNITS.getOrDefault(unit, 1L);
            long total = value * multiplier;

            if (total <= 0L || total > 86400L * 7L)
                return null;

            return (int) total;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static String formatSeconds(long seconds) {
        if (seconds >= 3600L) {
            long hours = seconds / 3600L;
            long minutes = (seconds % 3600L) / 60L;

            if (minutes > 0L)
                return hours + "h " + minutes + "m";

            return hours + "h";
        }

        if (seconds >= 60L) {
            long minutes = seconds / 60L;
            long rest = seconds % 60L;

            if (rest > 0L)
                return minutes + "m " + rest + "s";

            return minutes + "m";
        }

        return seconds + "s";
    }
}
