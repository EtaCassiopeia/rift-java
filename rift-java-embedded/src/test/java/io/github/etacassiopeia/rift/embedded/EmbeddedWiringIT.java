package io.github.etacassiopeia.rift.embedded;

import io.github.etacassiopeia.rift.EmbeddedOptions;
import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.Rift;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end wiring over the REAL engine: {@code Rift.embedded(...)} resolves the library, starts the
 * in-process engine through the {@code EmbeddedEngineProvider} ServiceLoader, passes the version
 * preflight, and drives a real imposter. Skips when no {@code -Drift.ffi.lib} is provided.
 */
class EmbeddedWiringIT {

    private static Path lib;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void requireLibrary() {
        String p = System.getProperty("rift.ffi.lib");
        assumeTrue(p != null && !p.isBlank() && Files.exists(Path.of(p)),
                "set -Drift.ffi.lib to a librift_ffi cdylib to run the embedded wiring test");
        lib = Path.of(p);
    }

    @Test
    void embeddedProviderIsDiscoverableAndReportsAvailable() {
        // -Drift.ffi.lib is set, so the provider can resolve a library.
        assertTrue(Rift.isEmbeddedAvailable(), "an embedded provider + resolvable library is present");
    }

    @Test
    void embeddedStartsTheRealEngineAndDrivesAnImposter() throws Exception {
        try (Rift rift = Rift.embedded(EmbeddedOptions.builder().libraryPath(lib).build())) {
            Imposter imposter = rift.create("{\"protocol\":\"http\",\"recordRequests\":true,\"stubs\":[{"
                    + "\"predicates\":[{\"equals\":{\"method\":\"GET\",\"path\":\"/ping\"}}],"
                    + "\"responses\":[{\"is\":{\"statusCode\":200,\"body\":\"pong\"}}]}]}");

            HttpResponse<String> resp = HTTP.send(
                    HttpRequest.newBuilder(URI.create(imposter.uri() + "/ping"))
                            .timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode());
            assertEquals("pong", resp.body());

            assertNotNull(imposter.recorded());
        }
    }
}
