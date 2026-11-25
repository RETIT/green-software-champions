package io.retit.greensoftware.web.format;

import io.retit.greensoftware.emissions.Periods;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formats the values of the pages, always in English, so that they are the same for
 * everyone. Missing values are shown as {@link #EMPTY}.
 */
public final class Format {

    static final String EMPTY = "—";

    private static final DateTimeFormatter LONG_PERIOD = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH);

    private Format() {
    }

    /** E.g. "9.9 kg", "12 kg" or "1.24 t". */
    public static String co2(Double kg) {
        if (!isFinite(kg)) {
            return EMPTY;
        }
        if (Math.abs(kg) >= 999.5) {
            return number(kg / 1_000, 2) + " t";
        }
        return number(kg, Math.abs(kg) < 9.95 ? 1 : 0) + " kg";
    }

    public static String percent(Double percent) {
        return percent(percent, 1);
    }

    public static String percent(Double percent, int digits) {
        return isFinite(percent) ? number(percent, digits) + "%" : EMPTY;
    }

    /** E.g. "↓ 5.0%" for a reduction, "↑ 5.0%" for an increase. */
    public static String reduction(Double percent) {
        return isFinite(percent) ? Trend.of(percent).arrow + percent(Math.abs(percent)) : EMPTY;
    }

    /** Formats a YYYY-MM period, e.g. as "March 2026". */
    public static String period(String period) {
        try {
            return LONG_PERIOD.format(Periods.toYearMonth(period));
        } catch (DateTimeException e) {
            return EMPTY;
        }
    }

    public static boolean isFinite(Double value) {
        return value != null && Double.isFinite(value);
    }

    public static BigDecimal round(double value, int digits) {
        return BigDecimal.valueOf(value).setScale(digits, RoundingMode.HALF_UP);
    }

    private static String number(double value, int digits) {
        return String.format(Locale.ENGLISH, "%,." + digits + "f", round(value, digits));
    }
}
