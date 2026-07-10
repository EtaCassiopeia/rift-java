package io.github.etacassiopeia.rift.testcontainers;

import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.Rift;
import io.github.etacassiopeia.rift.dsl.ImposterSpec;
import io.github.etacassiopeia.rift.junit5.InjectImposter;
import io.github.etacassiopeia.rift.junit5.RiftGolden;
import io.github.etacassiopeia.rift.junit5.RiftImposter;
import io.github.etacassiopeia.rift.junit5.RiftTest;
import io.github.etacassiopeia.rift.junit5.Transport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static io.github.etacassiopeia.rift.dsl.RiftDsl.imposter;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.okJson;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.onGet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EngineTestKit.engine;

/**
 * Golden-file capture/replay (#25 Part 2, AC2) end-to-end against a REAL rift engine in Docker.
 * Two {@code @RiftGolden} fixtures share one golden file: {@link CaptureFixture} runs first with the
 * file absent (CAPTURE — proxy to the in-container upstream, record, persist on class close), then
 * {@link ReplayFixture} runs with the file present and the upstream deleted (REPLAY — serve from the
 * file, no network). Two distinct classes rather than one class twice (re-running the same class in
 * one JVM discovers no tests the second time). Gated on {@code RIFT_IT}.
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RIFT_IT", matches = "1|true")
class RiftGoldenIT {

    static final String GOLDEN_FILE = "target/rift-golden-it/users.json";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Container
    static final RiftContainer RIFT = new RiftContainer().withImposterPorts(4545, 4700);

    @Test
    void goldenCaptureThenReplay() throws Exception {
        Files.deleteIfExists(Path.of(GOLDEN_FILE));
        clearAllImposters();
        System.setProperty("rift.golden.admin", RIFT.adminUri().toString());
        System.setProperty("rift.golden.users.url", "http://" + RIFT.getHost() + ":" + RIFT.getMappedPort(4545));

        // The upstream lives in the same engine; the golden imposter proxies to it via container localhost.
        try (Rift client = RIFT.client()) {
            client.create(imposter("upstream").port(4700)
                    .stub(onGet("/u/1").willReturn(okJson("{\"id\":1,\"src\":\"upstream\"}"))));
        }

        // CAPTURE — file absent: the fixture's test drives traffic through the golden imposter, which
        // proxies to the upstream and records it; the golden file is persisted on class close.
        engine("junit-jupiter").selectors(selectClass(CaptureFixture.class)).execute()
                .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
        assertTrue(Files.exists(Path.of(GOLDEN_FILE)), "CAPTURE wrote the golden file");

        // Free port 4545 and delete the upstream so REPLAY starts clean with the origin genuinely gone.
        clearAllImposters();

        // REPLAY — file present, upstream gone: served from the file. Green proves no network was touched.
        engine("junit-jupiter").selectors(selectClass(ReplayFixture.class)).execute()
                .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    private static void clearAllImposters() throws Exception {
        HTTP.send(HttpRequest.newBuilder(RIFT.adminUri().resolve("/imposters")).DELETE().build(),
                HttpResponse.BodyHandlers.discarding());
    }

    private static HttpResponse<String> hitUsers() throws Exception {
        String base = System.getProperty("rift.golden.users.url");
        return HTTP.send(
                HttpRequest.newBuilder(URI.create(base + "/u/1")).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @RiftTest(transport = Transport.CONNECT, adminUri = "${rift.golden.admin}")
    @RiftGolden(origin = "http://localhost:4700", file = GOLDEN_FILE)
    static class CaptureFixture {
        @RiftImposter
        static ImposterSpec users = imposter("users").port(4545).record();

        @InjectImposter("users")
        Imposter usersImposter;

        @Test
        void recordsThroughTheProxy() throws Exception {
            HttpResponse<String> response = hitUsers();
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"src\":\"upstream\""), "captured upstream body: " + response.body());
        }
    }

    @RiftTest(transport = Transport.CONNECT, adminUri = "${rift.golden.admin}")
    @RiftGolden(origin = "http://localhost:4700", file = GOLDEN_FILE)
    static class ReplayFixture {
        @RiftImposter
        static ImposterSpec users = imposter("users").port(4545).record();

        @InjectImposter("users")
        Imposter usersImposter;

        @Test
        void servesFromTheGoldenFileWithoutNetwork() throws Exception {
            HttpResponse<String> response = hitUsers();
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"src\":\"upstream\""), "replayed body from file: " + response.body());
        }
    }
}
