package io.retit.greensoftware.web.render;

import io.quarkus.oidc.OidcSession;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.spi.runtime.AuthorizationController;
import io.retit.greensoftware.service.Dashboard;
import io.retit.greensoftware.web.format.Format;
import io.retit.greensoftware.web.model.MapBubble;
import io.retit.greensoftware.web.model.Summary;
import io.retit.greensoftware.web.model.TeamCard;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.stream.Stream;

/** Renders the pages with Qute. They contain no user data, so they are the same for everyone. */
@ApplicationScoped
public class PageRenderer {

    @Inject
    Assets assets;

    /** Disabled in dev mode by {@code quarkus.security.auth.enabled-in-dev-mode=false}. */
    @Inject
    AuthorizationController authorizationController;

    /**
     * Only resolvable with {@code quarkus.oidc.enabled=true}, which as a build time setting
     * can't be read here.
     */
    @Inject
    Instance<OidcSession> oidcSession;

    /** Where Quarkus ends the session. */
    @ConfigProperty(name = "quarkus.oidc.logout.path")
    String logoutPath;

    /**
     * @param defaultPeriod the period of the pages without a {@code period} parameter
     * @param periods       the periods to choose from
     */
    public RenderedPage render(View view, Dashboard dashboard, String defaultPeriod, List<String> periods) {
        String period = dashboard.period();
        String query = period.equals(defaultPeriod) ? "" : "?period=" + period;
        Layout layout = new Layout(
                assets.urls(),
                Stream.of(View.values())
                        .map(v -> new Layout.NavLink(v.path + query, v.label, v == view))
                        .toList(),
                new Layout.PeriodPicker(view.path, periods.stream()
                        .map(p -> new Layout.Option(p, Format.period(p), p.equals(period)))
                        .toList()),
                logoutUrl(),
                false);
        return RenderedPage.of(template(view, layout, dashboard).render());
    }

    public RenderedPage renderDemo(View view, Dashboard dashboard) {
        Layout layout = new Layout(
                assets.urls(),
                Stream.of(View.values())
                        .map(v -> new Layout.NavLink(v.demoPath, v.label, v == view))
                        .toList(),
                null,
                logoutUrl(),
                true);
        return RenderedPage.of(template(view, layout, dashboard).render());
    }

    public String renderError(ErrorPage error) {
        Layout layout = new Layout(assets.urls(), List.of(), null, logoutUrl(), false);
        return Templates.error(layout, error).render();
    }

    private static TemplateInstance template(View view, Layout layout, Dashboard dashboard) {
        Summary summary = Summary.of(dashboard.summary());
        return switch (view) {
            case LEADERBOARD -> Templates.leaderboard(layout, summary,
                    dashboard.leaderboard().teams().stream().map(TeamCard::of).toList(),
                    dashboard.isEmpty());
            case MAP -> Templates.map(layout, summary,
                    dashboard.leaderboard().teams().stream().map(MapBubble::of).toList());
        };
    }

    /** Null if nobody logs in. */
    private String logoutUrl() {
        return oidcSession.isResolvable() && authorizationController.isAuthorizationEnabled() ? logoutPath : null;
    }
}
