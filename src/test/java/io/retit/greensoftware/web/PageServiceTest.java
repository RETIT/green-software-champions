package io.retit.greensoftware.web;

import io.quarkus.arc.ClientProxy;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.retit.greensoftware.achievement.Achievement;
import io.retit.greensoftware.achievement.AchievementEvaluation;
import io.retit.greensoftware.emissions.CarbonEntry;
import io.retit.greensoftware.emissions.Periods;
import io.retit.greensoftware.leaderboard.Dashboard;
import io.retit.greensoftware.team.Team;
import io.retit.greensoftware.web.render.PageRenderer;
import io.retit.greensoftware.web.render.RenderedPage;
import io.retit.greensoftware.web.render.View;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PageServiceTest {

    @Inject
    PageService pageService;

    @Inject
    PageRenderer renderer;

    /** Every team has data for this period, so it is the default one. */
    private final String period = Periods.previous();
    private final String older = Periods.previous(period);

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            Achievement.deleteAll();
            AchievementEvaluation.deleteAll();
            CarbonEntry.deleteAll();
            for (Team team : Team.<Team>listAll()) {
                createEntry(team, older, 100);
                createEntry(team, period, 200);
            }
        });
        assertTrue(pageService.refresh());
    }

    @Test
    void refresh_rendersTheDefaultPeriod() {
        for (View view : View.values()) {
            RenderedPage page = pageService.get(view, null).orElseThrow();
            assertTrue(html(page).contains("Company CO₂e in"));
            assertSame(page, pageService.get(view, "").orElseThrow());
            assertSame(page, pageService.get(view, period).orElseThrow(),
                    "The default period should have the same page as without a period");
        }
        assertNotSame(pageService.get(View.LEADERBOARD, null).orElseThrow(),
                pageService.get(View.MAP, null).orElseThrow());
    }

    @Test
    void get_keepsTheOldPagesUntilTheRefresh() {
        RenderedPage leaderboard = pageService.get(View.LEADERBOARD, null).orElseThrow();
        RenderedPage olderMap = pageService.get(View.MAP, older).orElseThrow();
        QuarkusTransaction.requiringNew().run(() -> CarbonEntry.update("totalCo2Kg = 300"));

        assertSame(leaderboard, pageService.get(View.LEADERBOARD, null).orElseThrow());
        assertSame(olderMap, pageService.get(View.MAP, older).orElseThrow());

        assertTrue(pageService.refresh());

        assertFalse(html(leaderboard).contains("1.20 t"));
        assertTrue(html(pageService.get(View.LEADERBOARD, null).orElseThrow()).contains("1.20 t"),
                "4 teams with 300 kg each");
        assertTrue(html(pageService.get(View.MAP, older).orElseThrow()).contains("1.20 t"),
                "The pages of the other periods should be dropped");
    }

    @Test
    void refresh_withoutChanges_keepsTheEtag() {
        RenderedPage before = pageService.get(View.LEADERBOARD, null).orElseThrow();
        assertTrue(pageService.refresh());
        RenderedPage after = pageService.get(View.LEADERBOARD, null).orElseThrow();

        assertNotSame(before, after);
        assertEquals(before.etag(), after.etag());
        assertEquals(before.gzipEtag(), after.gzipEtag());
    }

    @Test
    void refresh_thatFails_keepsTheOldPages() {
        RenderedPage leaderboard = pageService.get(View.LEADERBOARD, null).orElseThrow();
        RenderedPage olderLeaderboard = pageService.get(View.LEADERBOARD, older).orElseThrow();
        CountingRenderer counting = installRenderer();
        counting.failingView = View.MAP;

        assertFalse(pageService.refresh());

        assertSame(leaderboard, pageService.get(View.LEADERBOARD, null).orElseThrow(),
                "No page should be replaced, as the pages of one set belong together");
        assertSame(olderLeaderboard, pageService.get(View.LEADERBOARD, older).orElseThrow());
    }

    @Test
    void get_rendersOtherPeriodsOnce() {
        CountingRenderer counting = installRenderer();

        RenderedPage page = pageService.get(View.LEADERBOARD, older).orElseThrow();
        assertSame(page, pageService.get(View.LEADERBOARD, older).orElseThrow());

        assertEquals(List.of(older), counting.periods);
        assertTrue(html(page).contains("?period=" + older), "The navigation should keep the period");
    }

    @Test
    void get_thatFails_isTriedAgain() {
        CountingRenderer counting = installRenderer();
        counting.failingView = View.MAP;
        assertThrows(IllegalStateException.class, () -> pageService.get(View.MAP, older));

        counting.failingView = null;
        assertTrue(pageService.get(View.MAP, older).isPresent());
    }

    @Test
    void get_withoutDataForThePeriod_isEmptyAndRendersNothing() {
        CountingRenderer counting = installRenderer();

        assertTrue(pageService.get(View.LEADERBOARD, "1999-01").isEmpty());
        assertTrue(pageService.get(View.LEADERBOARD, Periods.current()).isEmpty());
        assertTrue(pageService.get(View.MAP, "nonsense").isEmpty());

        assertTrue(counting.periods.isEmpty());
    }

    @Test
    void withoutAnyData_thePreviousMonthIsTheDefault() {
        QuarkusTransaction.requiringNew().run(() -> CarbonEntry.deleteAll());
        assertTrue(pageService.refresh());

        String html = html(pageService.get(View.LEADERBOARD, null).orElseThrow());
        assertTrue(html.contains("No leaderboard data available"));
        assertTrue(html.contains("href=\"/demo\""));
        assertSame(pageService.get(View.LEADERBOARD, null).orElseThrow(),
                pageService.get(View.LEADERBOARD, Periods.previous()).orElseThrow());
        assertTrue(pageService.get(View.LEADERBOARD, older).isEmpty());
    }

    @Test
    void withoutOidc_hasNoLogoutLink() {
        assertFalse(html(pageService.get(View.LEADERBOARD, null).orElseThrow()).contains("/logout"));
    }

    @Test
    void getDemo_isRenderedOnceAndSurvivesARefresh() {
        RenderedPage demo = pageService.getDemo(View.LEADERBOARD);
        assertTrue(pageService.refresh());

        assertSame(demo, pageService.getDemo(View.LEADERBOARD));
        assertTrue(html(demo).contains("Demo data:"));
    }

    private CountingRenderer installRenderer() {
        CountingRenderer countingRenderer = new CountingRenderer(ClientProxy.unwrap(renderer));
        QuarkusMock.installMockForType(countingRenderer, PageRenderer.class);
        return countingRenderer;
    }

    private static String html(RenderedPage page) {
        return new String(page.html(), StandardCharsets.UTF_8);
    }

    private static void createEntry(Team team, String period, double totalCo2Kg) {
        CarbonEntry entry = new CarbonEntry();
        entry.team = team;
        entry.period = period;
        entry.totalCo2Kg = totalCo2Kg;
        entry.persist();
    }

    /** Records the rendered periods, and fails for {@link #failingView}. */
    static class CountingRenderer extends PageRenderer {

        final List<String> periods = new ArrayList<>();
        View failingView;

        private final PageRenderer delegate;

        CountingRenderer(PageRenderer delegate) {
            this.delegate = delegate;
        }

        @Override
        public RenderedPage render(View view, Dashboard dashboard, String defaultPeriod, List<String> options) {
            if (view == failingView) {
                throw new IllegalStateException("Failed to render " + view);
            }
            periods.add(dashboard.period());
            return delegate.render(view, dashboard, defaultPeriod, options);
        }
    }
}
