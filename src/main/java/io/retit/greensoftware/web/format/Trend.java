package io.retit.greensoftware.web.format;

/** The direction of a reduction, as shown with one decimal. */
public enum Trend {

    /** A reduction. */
    POSITIVE("positive", "↓ ", "📉"),

    /** An increase. */
    NEGATIVE("negative", "↑ ", "📈"),

    NEUTRAL("neutral", "", "➖");

    public final String cssClass;
    public final String arrow;
    public final String icon;

    Trend(String cssClass, String arrow, String icon) {
        this.cssClass = cssClass;
        this.arrow = arrow;
        this.icon = icon;
    }

    public static Trend of(Double reductionPercent) {
        if (!Format.isFinite(reductionPercent)) {
            return NEUTRAL;
        }
        return switch (Format.round(reductionPercent, 1).signum()) {
            case 0 -> NEUTRAL;
            case 1 -> POSITIVE;
            default -> NEGATIVE;
        };
    }
}
