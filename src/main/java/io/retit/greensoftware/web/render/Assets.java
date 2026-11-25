package io.retit.greensoftware.web.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * The URLs of the scripts and styles that Vite builds from src/main/webui. Without them, the
 * application refuses to start, before the initial sync, rather than serve bare pages.
 */
@Startup(Interceptor.Priority.APPLICATION)
@ApplicationScoped
public class Assets {

    private static final Logger LOG = Logger.getLogger(Assets.class);

    private static final String MANIFEST = "META-INF/resources/.vite/manifest.json";
    private static final String SCRIPT = "src/index.ts";
    private static final String STYLE = "src/styles/index.css";

    public record Urls(List<String> scripts, List<String> styles) {}

    @Inject
    ObjectMapper objectMapper;

    @Inject
    LaunchMode launchMode;

    private Urls urls;

    @PostConstruct
    void init() {
        if (launchMode == LaunchMode.DEVELOPMENT) {
            // Vite's dev server, which Quinoa proxies, transforms the sources on request
            urls = new Urls(List.of("/@vite/client", "/" + SCRIPT), List.of("/" + STYLE));
        } else {
            urls = readManifest();
        }
    }

    public Urls urls() {
        return urls;
    }

    private Urls readManifest() {
        try (InputStream manifest = Thread.currentThread().getContextClassLoader().getResourceAsStream(MANIFEST)) {
            if (manifest == null) {
                return withoutManifest();
            }
            return fromManifest(objectMapper.readTree(manifest));
        } catch (IOException e) {
            throw new UncheckedIOException("Invalid " + MANIFEST, e);
        }
    }

    /** Only the tests can do without, as they run without Node.js. */
    private Urls withoutManifest() {
        if (launchMode != LaunchMode.TEST) {
            throw new IllegalStateException("The frontend is not part of the application, as " + MANIFEST
                    + " is missing. Was it built with Quinoa disabled?");
        }
        LOG.warn("The frontend is not part of the application, so the pages have no scripts and styles.");
        return new Urls(List.of(), List.of());
    }

    /** @param manifest Vite's build manifest, the built file of each source file */
    static Urls fromManifest(JsonNode manifest) {
        return new Urls(List.of(url(manifest, SCRIPT)), List.of(url(manifest, STYLE)));
    }

    private static String url(JsonNode manifest, String source) {
        return "/" + manifest.required(source).required("file").asText();
    }
}
