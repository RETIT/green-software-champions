package io.retit.greensoftware.web.render;

import io.quarkus.qute.TemplateData;

/** The paths of the pages, in the templates e.g. {@code {Routes:DEMO}}. */
@TemplateData(namespace = TemplateData.SIMPLENAME)
public final class Routes {

    public static final String LEADERBOARD = "/";
    public static final String MAP = "/map";
    public static final String DEMO = "/demo";
    public static final String DEMO_MAP = DEMO + MAP;

    private Routes() {
    }
}
