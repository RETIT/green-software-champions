package io.retit.greensoftware.web.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.retit.greensoftware.service.Emissions;
import io.retit.greensoftware.service.HistoryPoint;
import io.retit.greensoftware.util.Periods;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The months end with June 2026, which has 30 days, like April; March and May have 31. */
class SparklineTest {

    @Test
    void withoutTwoMonthsWithData_isEmpty() {
        assertFalse(sparkline().exists());
        assertFalse(sparkline(100.0).exists());
        assertFalse(sparkline(null, null, 100.0).exists());
        assertNull(sparkline(100.0).points());
        assertTrue(sparkline(100.0, 200.0).exists());
    }

    @Test
    void drawsALineAndTheAreaBelow() {
        // Normalized to 30 days: 100, 150 and 200 kg
        Sparkline sparkline = sparkline(100.0, 155.0, 200.0);

        assertEquals(1, sparkline.segments().size());
        assertEquals("M0.0,26.0L50.0,15.0L100.0,4.0", sparkline.segments().getFirst().line());
        assertEquals("M0.0,26.0L50.0,15.0L100.0,4.0L100.0,30.0L0.0,30.0Z", sparkline.segments().getFirst().area());
        assertEquals(List.of(new Sparkline.Dot("current", 100, 13.33)), sparkline.dots());
    }

    @Test
    void smallChangesAreNotStretchedToTheFullHeight() {
        // 100 and 103 kg per 30 days in a range of 30.9 kg
        assertEquals("M0.0,16.07L100.0,13.93", sparkline(103.0 * 31 / 30 / 1.03, 103.0).segments().getFirst().line());
    }

    @Test
    void sameDailyEmissionsAreAFlatLine() {
        assertEquals("M0.0,15.0L100.0,15.0", sparkline(310.0, 300.0).segments().getFirst().line());
    }

    @Test
    void zerosAreDrawnAtTheBottom() {
        assertEquals("M0.0,26.0L100.0,26.0", sparkline(0.0, 0.0).segments().getFirst().line());
    }

    @Test
    void gapsInterruptTheLine() {
        Sparkline sparkline = sparkline(100.0, 100.0, null, 100.0, 100.0);

        assertEquals(2, sparkline.segments().size());
        assertTrue(sparkline.segments().get(0).line().startsWith("M0.0,"));
        assertTrue(sparkline.segments().get(1).line().startsWith("M75.0,"));
        assertEquals(List.of("current"), sparkline.dots().stream().map(Sparkline.Dot::cssClass).toList());
    }

    @Test
    void isolatedMonthsAreDots() {
        Sparkline sparkline = sparkline(100.0, null, 100.0, null, 100.0);

        assertEquals(List.of("isolated", "isolated", "current"),
                sparkline.dots().stream().map(Sparkline.Dot::cssClass).toList());
        assertEquals(List.of(0.0, 50.0, 100.0), sparkline.dots().stream().map(Sparkline.Dot::x).toList());
    }

    @Test
    void withoutDataInTheLastMonth_hasNoMarker() {
        assertTrue(sparkline(100.0, 100.0, null).dots().isEmpty());
    }

    @Test
    void pointsHaveTheTextsOfTheReadout() throws Exception {
        JsonNode points = new ObjectMapper().readTree(sparkline(100.0, 155.0, null, 120.0).points());

        // March: 31 days, no previous month
        assertEquals("""
                {"x":0.0,"y":86.67,"label":"March 2026","value":"100 kg","normalized":"(97 kg per 30 days)"}""",
                points.get(0).toString());
        // April: 30 days
        assertEquals("""
                {"x":33.33,"y":13.33,"label":"April 2026","value":"155 kg","change":"↑ 60.2%","trend":"negative"}""",
                points.get(1).toString());
        assertEquals("""
                {"x":66.67,"label":"May 2026","value":"—"}""", points.get(2).toString());
        assertFalse(points.get(3).has("change"), "The previous month has no data");
    }

    /** Of the months ending with June 2026, null for those without data. */
    private static Sparkline sparkline(Double... totals) {
        List<String> periods = Periods.window("2026-06", totals.length);
        List<HistoryPoint> history = new ArrayList<>();
        for (int i = 0; i < totals.length; i++) {
            history.add(new HistoryPoint(periods.get(i), Emissions.ofProviders(totals[i], null, null)));
        }
        return Sparkline.of("Trend", history);
    }
}
