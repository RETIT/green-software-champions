package io.retit.greensoftware.web.render;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.LaunchMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetsTest {

    @Test
    void readsTheBuiltFilesFromTheManifest() throws Exception {
        Assets.Urls urls = Assets.fromManifest(new ObjectMapper().readTree("""
                {
                  "src/index.ts": {"file": "assets/index-abc.js", "src": "src/index.ts", "isEntry": true},
                  "src/styles/index.css": {"file": "assets/index-def.css", "src": "src/styles/index.css", "isEntry": true}
                }"""));

        assertEquals(List.of("/assets/index-abc.js"), urls.scripts());
        assertEquals(List.of("/assets/index-def.css"), urls.styles());
    }

    @Test
    void failsIfAFileIsMissing() {
        assertThrows(IllegalArgumentException.class, () -> Assets.fromManifest(new ObjectMapper().readTree("{}")));
    }

    @Test
    void withoutFrontend_productionRefusesToStart() {
        IllegalStateException e = assertThrows(IllegalStateException.class, assetsIn(LaunchMode.NORMAL)::init);
        assertTrue(e.getMessage().contains("manifest.json is missing"), e.getMessage());
    }

    @Test
    void withoutFrontend_theTestsHaveNoScriptsAndStyles() {
        Assets assets = assetsIn(LaunchMode.TEST);
        assets.init();
        assertEquals(new Assets.Urls(List.of(), List.of()), assets.urls());
    }

    @Test
    void devMode_usesTheSourcesOfTheDevServer() {
        Assets assets = assetsIn(LaunchMode.DEVELOPMENT);
        assets.init();
        assertEquals(List.of("/@vite/client", "/src/index.ts"), assets.urls().scripts());
        assertEquals(List.of("/src/styles/index.css"), assets.urls().styles());
    }

    private static Assets assetsIn(LaunchMode launchMode) {
        Assets assets = new Assets();
        assets.objectMapper = new ObjectMapper();
        assets.launchMode = launchMode;
        return assets;
    }
}
