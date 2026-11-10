package net.omni.extraction.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class StringUtils {

    private static final Pattern SPACE_SPLIT_PATTERN = Pattern.compile(" ");
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");

    private StringUtils() {
    }

    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        String[] parts = SPACE_SPLIT_PATTERN.split(s);
        StringBuilder sb = new StringBuilder();

        for (String p : parts) {
            if (p.isEmpty())
                continue;

            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }

        return sb.toString().trim();
    }

    public static String toRoman(int n) {
        if (n == 1)
            return "I";

        if (n == 2)
            return "II";

        if (n == 3)
            return "III";

        if (n == 4)
            return "IV";

        if (n == 5)
            return "V";

        return String.valueOf(n);
    }

    public static String stripTags(String s) {
        if (s == null)
            return "";

        return TAG_PATTERN.matcher(s).replaceAll("");
    }

    public static Boolean parseBoolean(String value) {
        if (value == null) return null;

        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1" -> Boolean.TRUE;
            case "false", "no", "off", "0" -> Boolean.FALSE;
            default -> null;
        };
    }
}
