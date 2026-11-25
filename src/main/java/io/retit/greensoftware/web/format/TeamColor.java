package io.retit.greensoftware.web.format;

import java.util.regex.Pattern;

/** The teams' colors, which end up in {@code style} attributes. */
public final class TeamColor {

    private static final String FALLBACK = "#999999";

    /** Hex colors, names and color functions without nested functions, e.g. no {@code url()}. */
    private static final Pattern CSS_COLOR = Pattern.compile(
            "#(?:[0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})"
                    + "|[a-z]+"
                    + "|(?:rgba?|hsla?|hwb|lab|lch|oklab|oklch|color)\\([-+.,/%0-9a-z ]+\\)",
            Pattern.CASE_INSENSITIVE);

    private TeamColor() {
    }

    /** {@code color} if it is a CSS color, so that a team can't set other properties. */
    public static String cssColor(String color) {
        String trimmed = color != null ? color.trim() : "";
        return CSS_COLOR.matcher(trimmed).matches() ? trimmed : FALLBACK;
    }
}
