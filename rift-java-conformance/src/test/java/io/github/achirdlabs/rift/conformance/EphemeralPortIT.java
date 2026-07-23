package io.github.achirdlabs.rift.conformance;

import io.github.achirdlabs.rift.Imposter;
import io.github.achirdlabs.rift.Rift;
import io.github.achirdlabs.rift.SpawnOptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.Stream;

import static io.github.achirdlabs.rift.conformance.LiveEngine.gatedTo;
import static io.github.achirdlabs.rift.dsl.RiftDsl.imposter;
import static io.github.achirdlabs.rift.dsl.RiftDsl.okJson;
import static io.github.achirdlabs.rift.dsl.RiftDsl.onGet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The property that lets every other IT drop its fixed port (#162): an imposter created without one
 * gets a free port from the engine, reported back and immediately addressable.
 *
 * <p>{@link ItPortHygieneTest} enforces that the ITs ask for this; only a live engine can prove the
 * engine actually delivers it. The second half is what the issue was really about — two imposters
 * created the same way must land on <em>different</em> ports, so classes can no longer collide with
 * each other (two of them had been given 4597) or with whatever else happens to hold a number.
 *
 * <p>SPAWN-only because these tests call {@link io.github.achirdlabs.rift.Rift#spawn} directly — the
 * property under test is the engine's own port assignment, so the test drives a real engine process
 * rather than a transport.
 */
class EphemeralPortIT {

    /** Why this suite is one-lane. Stated once; both cases here are about the spawned engine. */
    private static final String SPAWN_ONLY =
            "spawns a real engine over the admin API — SPAWN lane only";

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @TestFactory
    Stream<DynamicTest> anEngineAssignedPortIsReportedBackAndServes() {
        return gatedTo(ConformanceTransport.SPAWN, SPAWN_ONLY,
                "an imposter with no port is assigned one that works", () -> {
            try (Rift rift = Rift.spawn(SpawnOptions.builder().build())) {
                Imposter imp = rift.create(imposter("ephemeral")
                        .stub(onGet("/ping").willReturn(okJson("{\"ok\":true}"))));

                // 0 would mean "the engine echoed the sentinel back instead of the real binding",
                // which would still fail later and much less legibly.
                assertTrue(imp.port() > 0, "the engine reports the port it actually bound: " + imp.port());
                assertEquals(imp.port(), imp.uri().getPort(), "uri() is built from the assigned port");
                assertEquals(200, get(imp.uri() + "/ping"), "the assigned port actually serves");
            }
        });
    }

    @TestFactory
    Stream<DynamicTest> twoPortlessImpostersNeverLandOnTheSamePort() {
        return gatedTo(ConformanceTransport.SPAWN, SPAWN_ONLY,
                "two port-less imposters coexist on distinct ports", () -> {
            try (Rift rift = Rift.spawn(SpawnOptions.builder().build())) {
                Imposter first = rift.create(imposter("ephemeral-a")
                        .stub(onGet("/who").willReturn(okJson("{\"who\":\"a\"}"))));
                Imposter second = rift.create(imposter("ephemeral-b")
                        .stub(onGet("/who").willReturn(okJson("{\"who\":\"b\"}"))));

                assertNotEquals(first.port(), second.port(),
                        "the engine must not hand the same port to two live imposters");
                // Both still serving proves they genuinely coexist rather than the second having
                // silently displaced the first.
                assertEquals(200, get(first.uri() + "/who"));
                assertEquals(200, get(second.uri() + "/who"));
            }
        });
    }

    private static int get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }
}
