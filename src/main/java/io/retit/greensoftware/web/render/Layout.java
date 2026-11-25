package io.retit.greensoftware.web.render;

import java.util.List;

/**
 * What surrounds the content of a page.
 *
 * @param nav          empty on error pages
 * @param periodPicker null if there is nothing to pick
 * @param logoutUrl    null without a log-out link
 * @param demo         whether the page shows demo data
 */
public record Layout(Assets.Urls assets, List<NavLink> nav, PeriodPicker periodPicker, String logoutUrl,
                     boolean demo) {

    public record NavLink(String url, String label, boolean active) {}

    /** @param action the path of the view, to which the period is added */
    public record PeriodPicker(String action, List<Option> options) {}

    public record Option(String period, String label, boolean selected) {}
}
