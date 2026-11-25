package io.retit.greensoftware.web;

import io.retit.greensoftware.service.CompanySummary;
import io.retit.greensoftware.service.Dashboard;
import io.retit.greensoftware.service.Leaderboard;
import io.retit.greensoftware.service.LeaderboardService;
import io.retit.greensoftware.web.render.PageRenderer;
import io.retit.greensoftware.web.render.RenderedPage;
import io.retit.greensoftware.web.render.View;
import jakarta.ws.rs.core.EntityTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link PageService} with requests and changes of the data at the same time, and its
 * recovery if rendering the pages fails at startup.
 */
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class PageServiceConcurrencyTest {

    private static final String PERIOD = "2026-03";
    private static final String OLDER = "2026-02";

    private final PageService pageService = new PageService();
    private final FakeLeaderboardService data = new FakeLeaderboardService();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @BeforeEach
    void setUp() {
        pageService.leaderboardService = data;
        pageService.renderer = new FakeRenderer();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    // -------------------------------------------------------------------------
    // Changes and requests at the same time
    // -------------------------------------------------------------------------

    @Test
    void concurrentRefreshes_endWithTheLastCommit() throws Exception {
        assertTrue(pageService.refresh());
        Pause firstRead = data.pauseNextRead();
        Future<Boolean> first = executor.submit(pageService::refresh);
        firstRead.awaitReached();

        // Committed after the first refresh has read the data
        data.version = 2;
        FutureTask<Boolean> second = new FutureTask<>(pageService::refresh);
        Thread secondThread = Thread.ofPlatform().start(second);
        awaitBlockedOrTerminated(secondThread);
        firstRead.end();

        assertTrue(first.get());
        assertTrue(second.get());
        assertEquals("2", versionOf(View.LEADERBOARD, null), "The first refresh should not overwrite the second");
        assertEquals("2", versionOf(View.MAP, null));
    }

    @Test
    void pageRenderedDuringARefresh_isDroppedWithTheOldPages() throws Exception {
        assertTrue(pageService.refresh());
        Pause read = data.pauseNextRead();
        Future<String> request = executor.submit(() -> versionOf(View.LEADERBOARD, OLDER));
        read.awaitReached();

        data.version = 2;
        assertTrue(pageService.refresh());
        read.end();

        assertEquals("1", request.get(), "The request has read the data before the change");
        assertEquals("2", versionOf(View.LEADERBOARD, OLDER), "The outdated page should not be kept");
    }

    @Test
    void requestsDuringARefresh_getTheOldPagesWithoutWaiting() throws Exception {
        assertTrue(pageService.refresh());
        assertEquals("1", versionOf(View.MAP, OLDER));
        data.version = 2;
        Pause read = data.pauseNextRead();
        Future<Boolean> refresh = executor.submit(pageService::refresh);
        read.awaitReached();

        assertEquals("1", versionOf(View.LEADERBOARD, null));
        assertEquals("1", versionOf(View.MAP, OLDER));

        read.end();
        assertTrue(refresh.get());
        assertEquals("2", versionOf(View.LEADERBOARD, null));
        assertEquals("2", versionOf(View.MAP, OLDER));
    }

    @Test
    void concurrentRequestsForTheSamePage_renderItOnce() throws Exception {
        assertTrue(pageService.refresh());
        data.reads.set(0);
        assertAllTheSame(requestsDuringTheFirstRead(() -> page(View.LEADERBOARD, OLDER)));
        assertEquals(1, data.reads.get());
    }

    // -------------------------------------------------------------------------
    // Recovery from a failure at startup
    // -------------------------------------------------------------------------

    @Test
    void firstRequestAfterAFailedStart_rendersThePages() {
        data.failing = true;
        assertFalse(pageService.refresh());
        data.failing = false;

        RenderedPage page = page(View.LEADERBOARD, null);

        assertSame(page, page(View.LEADERBOARD, PERIOD));
        assertEquals("1", versionOf(View.MAP, null));
        assertEquals(2, data.reads.get(), "The pages should be rendered once after the failure");
    }

    @Test
    void everyRequestTriesAgainWhileRenderingFails() {
        data.failing = true;
        assertThrows(IllegalStateException.class, () -> page(View.LEADERBOARD, null));
        assertThrows(IllegalStateException.class, () -> page(View.LEADERBOARD, null));
        assertEquals(2, data.reads.get());
    }

    @Test
    void concurrentFirstRequests_renderThePagesOnce() throws Exception {
        assertAllTheSame(requestsDuringTheFirstRead(() -> page(View.LEADERBOARD, null)));
        assertEquals(1, data.reads.get());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private RenderedPage page(View view, String period) {
        return pageService.get(view, period).orElseThrow();
    }

    /** The version of the data a page shows. */
    private String versionOf(View view, String period) {
        return page(view, period).etag().getValue();
    }

    /** Sends several requests, of which the others arrive while the first one reads the data. */
    private List<RenderedPage> requestsDuringTheFirstRead(Callable<RenderedPage> request) throws Exception {
        Pause read = data.pauseNextRead();
        List<FutureTask<RenderedPage>> requests = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            FutureTask<RenderedPage> task = new FutureTask<>(request);
            requests.add(task);
            threads.add(Thread.ofPlatform().start(task));
            if (i == 0) {
                read.awaitReached();
            }
        }
        for (Thread thread : threads.subList(1, threads.size())) {
            awaitBlockedOrTerminated(thread);
        }
        read.end();

        List<RenderedPage> pages = new ArrayList<>();
        for (FutureTask<RenderedPage> task : requests) {
            pages.add(task.get());
        }
        return pages;
    }

    private static void assertAllTheSame(List<RenderedPage> pages) {
        pages.forEach(page -> assertSame(pages.getFirst(), page));
    }

    /** Until the thread waits for a lock, or without one, is done. */
    private static void awaitBlockedOrTerminated(Thread thread) throws InterruptedException {
        while (thread.getState() != Thread.State.BLOCKED && thread.getState() != Thread.State.TERMINATED) {
            Thread.sleep(1);
        }
    }

    /** Holds up a read after it got the data. */
    private static class Pause {

        private final CountDownLatch reached = new CountDownLatch(1);
        private final CountDownLatch ended = new CountDownLatch(1);

        void awaitReached() throws InterruptedException {
            assertTrue(reached.await(10, TimeUnit.SECONDS));
        }

        void end() {
            ended.countDown();
        }

        private void hold() {
            reached.countDown();
            try {
                assertTrue(ended.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Returns the version of the data as the total. */
    private static class FakeLeaderboardService extends LeaderboardService {

        final AtomicInteger reads = new AtomicInteger();
        volatile int version = 1;
        volatile boolean failing;

        private final AtomicReference<Pause> nextPause = new AtomicReference<>();

        Pause pauseNextRead() {
            Pause pause = new Pause();
            nextPause.set(pause);
            return pause;
        }

        @Override
        public Dashboard getDashboard(String period) {
            reads.incrementAndGet();
            if (failing) {
                throw new IllegalStateException("Database unavailable");
            }
            String shown = period != null ? period : PERIOD;
            CompanySummary summary = new CompanySummary(shown, version, null, null, 0, List.of());
            Pause pause = nextPause.getAndSet(null);
            if (pause != null) {
                pause.hold();
            }
            return new Dashboard(summary, new Leaderboard(shown, List.of()));
        }

        @Override
        public List<String> periodsWithData() {
            return List.of(PERIOD, OLDER);
        }
    }

    /** Renders the version of the data as the ETag. */
    private static class FakeRenderer extends PageRenderer {

        @Override
        public RenderedPage render(View view, Dashboard dashboard, String defaultPeriod, List<String> periods) {
            EntityTag version = new EntityTag(String.valueOf((int) dashboard.summary().totalCo2Kg()));
            return new RenderedPage(new byte[0], version, new byte[0], version);
        }
    }
}
