package io.retit.greensoftware.web.render;

/**
 * @param linkUrl where to go instead, or null
 */
public record ErrorPage(int status, String message, String linkUrl, String linkLabel) {

    public static final ErrorPage MALFORMED_PERIOD = new ErrorPage(400,
            "The period is not a month in the format YYYY-MM.", Routes.LEADERBOARD, "Show the latest period");

    public static final ErrorPage NO_ACCESS = new ErrorPage(403,
            "Your account has no access to Green Cloud Champions. Ask an administrator for the role “user”.",
            null, null);

    public static final ErrorPage NO_DATA = new ErrorPage(404,
            "There is no data for this period.", Routes.LEADERBOARD, "Show the latest period");

    public static final ErrorPage FAILED = new ErrorPage(500,
            "The page could not be rendered.", Routes.DEMO, "Show demo data");
}
