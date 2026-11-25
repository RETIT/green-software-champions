package io.retit.greensoftware.web;

import io.quarkus.security.ForbiddenException;
import io.retit.greensoftware.util.Periods;
import io.retit.greensoftware.web.render.ErrorPage;
import io.retit.greensoftware.web.render.PageRenderer;
import io.retit.greensoftware.web.render.RenderedPage;
import io.retit.greensoftware.web.render.Routes;
import io.retit.greensoftware.web.render.View;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.function.Supplier;

/** The pages of the dashboard, see {@link PageService}. */
@Path("/")
@Produces(PageResource.HTML)
@RolesAllowed("user")
public class PageResource {

    static final String HTML = "text/html;charset=UTF-8";

    private static final Logger LOG = Logger.getLogger(PageResource.class);

    @Inject
    PageService pageService;

    @Inject
    PageRenderer renderer;

    @Context
    Request request;

    @Context
    HttpHeaders headers;

    /** @param period YYYY-MM, or empty for the latest period every team has data for */
    @GET
    @Path(Routes.LEADERBOARD)
    @Operation(hidden = true)
    public Response leaderboard(@QueryParam("period") String period) {
        return page(View.LEADERBOARD, period);
    }

    /** @param period like {@link #leaderboard} */
    @GET
    @Path(Routes.MAP)
    @Operation(hidden = true)
    public Response map(@QueryParam("period") String period) {
        return page(View.MAP, period);
    }

    @GET
    @Path(Routes.DEMO)
    @Operation(hidden = true)
    public Response demoLeaderboard() {
        return respond(() -> Optional.of(pageService.getDemo(View.LEADERBOARD)));
    }

    @GET
    @Path(Routes.DEMO_MAP)
    @Operation(hidden = true)
    public Response demoMap() {
        return respond(() -> Optional.of(pageService.getDemo(View.MAP)));
    }

    /** Logged in without the role "user". Only for the pages, not for the API. */
    @ServerExceptionMapper
    public Response forbidden(ForbiddenException e) {
        return error(ErrorPage.NO_ACCESS);
    }

    private Response page(View view, String period) {
        if (period != null && !period.isBlank()) {
            try {
                Periods.toYearMonth(period);
            } catch (DateTimeParseException e) {
                return error(ErrorPage.MALFORMED_PERIOD);
            }
        }
        return respond(() -> pageService.get(view, period));
    }

    private Response respond(Supplier<Optional<RenderedPage>> page) {
        Optional<RenderedPage> rendered;
        try {
            rendered = page.get();
        } catch (RuntimeException e) {
            LOG.error("Rendering a page failed.", e);
            return error(ErrorPage.FAILED);
        }
        return rendered.map(this::ok).orElseGet(() -> error(ErrorPage.NO_DATA));
    }

    private Response ok(RenderedPage page) {
        boolean gzip = AcceptEncoding.allowsGzip(headers.getHeaderString(HttpHeaders.ACCEPT_ENCODING));
        EntityTag etag = gzip ? page.gzipEtag() : page.etag();

        Response.ResponseBuilder response = request.evaluatePreconditions(etag);
        if (response == null) {
            response = Response.ok(gzip ? page.gzip() : page.html());
            if (gzip) {
                response.header(HttpHeaders.CONTENT_ENCODING, "gzip");
            }
        }
        return response.tag(etag)
                // Browsers revalidate with every navigation; shared caches would bypass the login
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING)
                .build();
    }

    private Response error(ErrorPage error) {
        return Response.status(error.status())
                .type(HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .entity(renderer.renderError(error))
                .build();
    }
}
