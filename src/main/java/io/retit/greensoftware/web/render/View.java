package io.retit.greensoftware.web.render;

/** The views of the dashboard, each a page per period. */
public enum View {

    LEADERBOARD(Routes.LEADERBOARD, Routes.DEMO, "🏆 Leaderboard"),
    MAP(Routes.MAP, Routes.DEMO_MAP, "🗺️ World Map");

    public final String path;
    public final String demoPath;

    /** In the navigation. */
    public final String label;

    View(String path, String demoPath, String label) {
        this.path = path;
        this.demoPath = demoPath;
        this.label = label;
    }
}
