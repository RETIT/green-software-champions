package io.retit.greensoftware.web;

import io.retit.greensoftware.web.render.RenderedPage;
import io.retit.greensoftware.web.render.View;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The pages rendered from one state of the data. Those of a period are rendered together,
 * a page per view, so that they show the same data and it is read once.
 */
final class PageSet {

    private final String defaultPeriod;
    private final List<String> periods;
    private final Map<String, Map<View, RenderedPage>> pages = new ConcurrentHashMap<>();

    PageSet(String defaultPeriod, List<String> periodsWithData) {
        this.defaultPeriod = defaultPeriod;
        this.periods = Stream.concat(Stream.of(defaultPeriod), periodsWithData.stream())
                .distinct()
                // YYYY-MM strings sort chronologically
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    String defaultPeriod() {
        return defaultPeriod;
    }

    /** The periods that have pages: those with data and the default one, latest first. */
    List<String> periods() {
        return periods;
    }

    /** The pages of the period by view, rendered by {@code render} at the first request. */
    Map<View, RenderedPage> get(String period, Supplier<Map<View, RenderedPage>> render) {
        return pages.computeIfAbsent(period, key -> render.get());
    }
}
