package io.retit.greensoftware.web.render;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.retit.greensoftware.web.model.MapBubble;
import io.retit.greensoftware.web.model.Summary;
import io.retit.greensoftware.web.model.TeamCard;

import java.util.List;

/** The pages, in src/main/resources/templates. */
@CheckedTemplate
final class Templates {

    private Templates() {
    }

    /** @param empty whether there is nothing to show */
    static native TemplateInstance leaderboard(Layout layout, Summary summary, List<TeamCard> teams, boolean empty);

    static native TemplateInstance map(Layout layout, Summary summary, List<MapBubble> bubbles);

    static native TemplateInstance error(Layout layout, ErrorPage error);
}
