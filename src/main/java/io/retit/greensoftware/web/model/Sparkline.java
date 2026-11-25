package io.retit.greensoftware.web.model;

import io.retit.greensoftware.service.HistoryPoint;
import io.retit.greensoftware.util.Periods;
import io.retit.greensoftware.util.Reductions;
import io.retit.greensoftware.web.format.Format;
import io.retit.greensoftware.web.format.Json;
import io.retit.greensoftware.web.format.Trend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Trend line of monthly emissions with a marker on the last month, empty until two months
 * have data. The months are normalized to {@value #NORMALIZED_DAYS} days, so months of
 * different length are comparable.
 *
 * @param label    accessible description of what the line shows
 * @param segments the runs of consecutive months with data
 * @param points   all months as JSON for the hover readout, see {@link Point}
 */
public record Sparkline(String label, List<Segment> segments, List<Dot> dots, String points) {

    /** Cancels out in comparisons, only sets the magnitude of shown values. */
    private static final int NORMALIZED_DAYS = 30;

    /** viewBox height; the width is 100, so x coordinates are percentages. */
    private static final double HEIGHT = 30;

    /** Vertical padding, so the line never touches the edges. */
    private static final double PADDING = 4;

    /**
     * Minimum y-range relative to the largest value, so that small month-to-month
     * noise stays small instead of being stretched to the full height.
     */
    private static final double MIN_SPAN_RATIO = 0.3;

    /** Path data of a line and the area below it. */
    public record Segment(String line, String area) {}

    /** @param x position in percent, like {@code y} */
    public record Dot(String cssClass, double x, double y) {}

    /**
     * A month, with its texts preformatted. Null if not shown.
     *
     * @param x position of the dot in percent, like {@code y}, which is null without data
     */
    public record Point(double x, Double y, String label, String value, String normalized,
                        String change, String trend) {}

    /** @param history consecutive months, oldest first */
    static Sparkline of(String label, List<HistoryPoint> history) {
        List<Double> values = history.stream().map(Sparkline::normalize).toList();
        List<Double> existing = values.stream().filter(Objects::nonNull).toList();
        if (existing.size() < 2) {
            return new Sparkline(label, List.of(), List.of(), null);
        }
        Plot plot = Plot.of(values, existing);
        List<Point> points = IntStream.range(0, history.size())
                .mapToObj(i -> point(history, values, plot, i))
                .toList();
        return new Sparkline(label, segments(values, plot), dots(values, plot), Json.write(points));
    }

    /** Whether the line is not empty. */
    public boolean exists() {
        return !segments.isEmpty();
    }

    private static Double normalize(HistoryPoint point) {
        Double co2Kg = point.emissions().totalCo2Kg();
        return co2Kg != null ? co2Kg / Periods.lengthInDays(point.period()) * NORMALIZED_DAYS : null;
    }

    /** Where values are plotted: [{@code low}, {@code high}] over the height without the padding. */
    private record Plot(int months, double low, double high) {

        /**
         * Centered on the data and at least MIN_SPAN_RATIO of the largest value high,
         * shifted up rather than reaching below 0.
         */
        static Plot of(List<Double> values, List<Double> existing) {
            double min = Collections.min(existing);
            double max = Collections.max(existing);
            double span = Math.max(max - min, MIN_SPAN_RATIO * Math.abs(max));
            double low = (min + max) / 2 - span / 2;
            if (min >= 0 && low < 0) {
                low = 0;
            }
            return new Plot(values.size(), low, low + span);
        }

        double x(int index) {
            return round(months > 1 ? index / (months - 1.0) * 100 : 50);
        }

        double y(double value) {
            // Only all-zero data has an empty range; draw it at the bottom
            return round(high == low
                    ? HEIGHT - PADDING
                    : PADDING + (1 - (value - low) / (high - low)) * (HEIGHT - 2 * PADDING));
        }

        double yPercent(double value) {
            return round(y(value) / HEIGHT * 100);
        }

        private static double round(double coordinate) {
            return Format.round(coordinate, 2).doubleValue();
        }
    }

    private static List<Segment> segments(List<Double> values, Plot plot) {
        List<Segment> segments = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= values.size(); i++) {
            if (i == values.size() || values.get(i) == null) {
                if (start < i) {
                    segments.add(segment(values, plot, start, i - 1));
                }
                start = i + 1;
            }
        }
        return segments;
    }

    private static Segment segment(List<Double> values, Plot plot, int first, int last) {
        StringBuilder line = new StringBuilder();
        for (int i = first; i <= last; i++) {
            line.append(i == first ? "M" : "L").append(plot.x(i)).append(',').append(plot.y(values.get(i)));
        }
        return new Segment(line.toString(),
                line + "L" + plot.x(last) + "," + HEIGHT + "L" + plot.x(first) + "," + HEIGHT + "Z");
    }

    private static List<Dot> dots(List<Double> values, Plot plot) {
        List<Dot> dots = new ArrayList<>();
        int last = values.size() - 1;
        for (int i = 0; i <= last; i++) {
            if (values.get(i) == null) {
                continue;
            }
            boolean isolated = (i == 0 || values.get(i - 1) == null) && (i == last || values.get(i + 1) == null);
            // Isolated months have no line, so they are shown as small dots
            if (i == last || isolated) {
                dots.add(new Dot(i == last ? "current" : "isolated", plot.x(i), plot.yPercent(values.get(i))));
            }
        }
        return dots;
    }

    private static Point point(List<HistoryPoint> history, List<Double> values, Plot plot, int i) {
        HistoryPoint month = history.get(i);
        Double value = values.get(i);
        Double reduction = i > 0 ? Reductions.percent(values.get(i - 1), value) : null;
        // Equal to the value for months of the normalized length
        boolean showsNormalized = value != null && Periods.lengthInDays(month.period()) != NORMALIZED_DAYS;
        return new Point(
                plot.x(i),
                value != null ? plot.yPercent(value) : null,
                Format.period(month.period()),
                Format.co2(month.emissions().totalCo2Kg()),
                showsNormalized ? "(" + Format.co2(value) + " per " + NORMALIZED_DAYS + " days)" : null,
                reduction != null ? Format.reduction(reduction) : null,
                reduction != null ? Trend.of(reduction).cssClass : null);
    }
}
