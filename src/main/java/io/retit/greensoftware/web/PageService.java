package io.retit.greensoftware.web;

import io.retit.greensoftware.service.Dashboard;
import io.retit.greensoftware.service.DashboardDataChanged;
import io.retit.greensoftware.service.DemoDataService;
import io.retit.greensoftware.service.LeaderboardService;
import io.retit.greensoftware.util.Periods;
import io.retit.greensoftware.web.render.PageRenderer;
import io.retit.greensoftware.web.render.RenderedPage;
import io.retit.greensoftware.web.render.View;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Keeps the rendered pages in memory. Whenever the data changes, {@link #refresh} renders
 * the pages of the default period again and drops those of the others, which are rendered
 * at their next request.
 */
@ApplicationScoped
public class PageService {

    private static final Logger LOG = Logger.getLogger(PageService.class);

    @Inject
    LeaderboardService leaderboardService;

    @Inject
    DemoDataService demoDataService;

    @Inject
    PageRenderer renderer;

    /** Null until the first successful refresh. */
    private volatile PageSet pages;

    /** Only of one period, null until the first request. */
    private volatile PageSet demoPages;

    /** Right after the commit, or without a transaction, at once, so the event's source waits for it. */
    void onDataChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) DashboardDataChanged event) {
        refresh();
    }

    /**
     * Never throws, so that it doesn't fail the change of the data. Concurrent changes
     * wait for each other here, so the last refresh sees the last commit.
     *
     * <p>Suspends any transaction of the caller, so that only committed data is rendered,
     * and as an event's transaction is still there but can't be used after its commit.
     *
     * @return whether rendering succeeded; otherwise, the previous pages are kept
     */
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public synchronized boolean refresh() {
        try {
            Dashboard dashboard = leaderboardService.getDashboard(null);
            PageSet refreshed = new PageSet(dashboard.period(), leaderboardService.periodsWithData());
            for (View view : View.values()) {
                refreshed.get(view, refreshed.defaultPeriod(), () -> render(view, dashboard, refreshed));
            }
            pages = refreshed;
            return true;
        } catch (RuntimeException e) {
            LOG.error("Rendering the pages failed, keeping the previous ones.", e);
            return false;
        }
    }

    /**
     * @param period YYYY-MM, or blank for the default period
     * @return empty if there is no data for {@code period}
     * @throws RuntimeException if the page could not be rendered
     */
    public Optional<RenderedPage> get(View view, String period) {
        // Read once, so that a page rendered during a refresh is dropped with the old set
        PageSet current = currentPages();
        String shown = period == null || period.isBlank() ? current.defaultPeriod() : period;
        if (!current.periods().contains(shown)) {
            return Optional.empty();
        }
        return Optional.of(current.get(view, shown,
                () -> render(view, leaderboardService.getDashboard(shown), current)));
    }

    /** Renders the pages if that failed at startup, so that the dashboard recovers by itself. */
    private PageSet currentPages() {
        PageSet current = pages;
        return current != null ? current : renderFirstPages();
    }

    /** Requests wait for each other here, so only one of them renders the pages. */
    private synchronized PageSet renderFirstPages() {
        if (pages == null && !refresh()) {
            throw new IllegalStateException("The pages could not be rendered, see the log");
        }
        return pages;
    }

    /** The page with demo data of the previous month, rendered at the first request. */
    public RenderedPage getDemo(View view) {
        String period = Periods.previous();
        PageSet current = demoPages;
        if (current == null || !current.defaultPeriod().equals(period)) {
            current = new PageSet(period, List.of());
            demoPages = current;
        }
        return current.get(view, period,
                () -> renderer.renderDemo(view, demoDataService.getDashboard(period)));
    }

    private RenderedPage render(View view, Dashboard dashboard, PageSet pageSet) {
        return renderer.render(view, dashboard, pageSet.defaultPeriod(), pageSet.periods());
    }
}
