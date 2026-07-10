package io.github.etacassiopeia.rift.embedded;

import io.github.etacassiopeia.rift.error.RiftException;
import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end integration of {@link EmbeddedTransport} over the REAL {@code librift_ffi} engine (loaded
 * from {@code -Drift.ffi.lib}). Exercises the direct-FFM data plane (drive a real imposter with HTTP,
 * read it back via {@code recorded()}), flow-state and spaces, {@code buildInfo}, the lazily-started
 * admin plane and the operations delegated to it, and error mapping. Skips cleanly when no lib is set.
 */
class EmbeddedTransportIT {

    private static Path lib;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void requireLibrary() {
        String p = System.getProperty("rift.ffi.lib");
        assumeTrue(p != null && !p.isBlank() && Files.exists(Path.of(p)),
                "set -Drift.ffi.lib to a librift_ffi cdylib to run the embedded FFM integration tests");
        lib = Path.of(p);
    }

    private static EmbeddedTransport open() {
        return EmbeddedTransport.open(lib);
    }

    /** A recording HTTP imposter that answers GET /ping with 200 "pong". */
    private static JsonValue recordingImposter() {
        return JsonValue.parse("{\"protocol\":\"http\",\"recordRequests\":true,\"stubs\":[{"
                + "\"predicates\":[{\"equals\":{\"method\":\"GET\",\"path\":\"/ping\"}}],"
                + "\"responses\":[{\"is\":{\"statusCode\":200,\"body\":\"pong\"}}]}]}");
    }

    /**
     * The engine only backs flow-state/spaces with a real store when the def declares one (uniform
     * across transports) — so an imposter exercising those APIs opts in via {@code _rift.flowState}.
     */
    private static JsonValue flowStateImposter() {
        return JsonValue.parse("{\"protocol\":\"http\",\"recordRequests\":true,"
                + "\"_rift\":{\"flowState\":{\"backend\":\"inmemory\"}},\"stubs\":[{"
                + "\"predicates\":[{\"equals\":{\"method\":\"GET\",\"path\":\"/ping\"}}],"
                + "\"responses\":[{\"is\":{\"statusCode\":200,\"body\":\"pong\"}}]}]}");
    }

    private static int portOf(JsonValue created) {
        assertTrue(created instanceof JsonObject, "createImposter returns an object");
        JsonValue port = ((JsonObject) created).get("port");
        assertTrue(port instanceof io.github.etacassiopeia.rift.json.JsonNumber, "with a port");
        return ((io.github.etacassiopeia.rift.json.JsonNumber) port).asInt();
    }

    /** recorded()/spaceRecorded() may return {"requests":[…]} or a bare array — normalize to the list. */
    private static List<JsonValue> requests(JsonValue recorded) {
        if (recorded instanceof JsonArray arr) {
            return arr.items();
        }
        if (recorded instanceof JsonObject obj && obj.get("requests") instanceof JsonArray arr) {
            return arr.items();
        }
        throw new AssertionError("unexpected recorded shape: " + recorded.toJson());
    }

    private static String pathOf(JsonValue request) {
        return request instanceof JsonObject o && o.get("path") instanceof JsonString s ? s.value() : null;
    }

    @Test
    void lifecycleOpensAndClosesCleanly() {
        try (EmbeddedTransport t = open()) {
            assertNotNull(t);
        }
    }

    @Test
    void buildInfoReportsAVersion() {
        try (EmbeddedTransport t = open()) {
            JsonValue info = t.buildInfo();
            assertTrue(info instanceof JsonObject o && o.get("version") instanceof JsonString v
                    && !v.value().isBlank(), "buildInfo has a non-blank version: " + info.toJson());
        }
    }

    @Test
    void dataPlaneRecordsRealTraffic() throws Exception {
        try (EmbeddedTransport t = open()) {
            int port = portOf(t.createImposter(recordingImposter()));

            HttpResponse<String> resp = HTTP.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/ping"))
                            .timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode());
            assertEquals("pong", resp.body());

            List<JsonValue> recorded = requests(t.recorded(port));
            assertTrue(recorded.stream().anyMatch(r -> "/ping".equals(pathOf(r))),
                    "the GET /ping was recorded: " + t.recorded(port).toJson());

            t.deleteImposter(port);
        }
    }

    @Test
    void flowStateRoundTrips() {
        try (EmbeddedTransport t = open()) {
            int port = portOf(t.createImposter(flowStateImposter()));

            assertEquals(Optional.empty(), t.flowStateGet(port, "flow-A", "k"),
                    "absent key is empty, not an error");
            t.flowStatePut(port, "flow-A", "k", JsonValue.parse("{\"n\":1}"));
            Optional<JsonValue> got = t.flowStateGet(port, "flow-A", "k");
            assertTrue(got.isPresent(), "put value is readable");
            t.flowStateDelete(port, "flow-A", "k");
            assertEquals(Optional.empty(), t.flowStateGet(port, "flow-A", "k"), "deleted key is empty");

            t.deleteImposter(port);
        }
    }

    @Test
    void spacesRoundTrip() {
        try (EmbeddedTransport t = open()) {
            int port = portOf(t.createImposter(flowStateImposter()));

            t.spaceAddStub(port, "flow-B", JsonValue.parse(
                    "{\"predicates\":[{\"equals\":{\"path\":\"/scoped\"}}],\"responses\":[{\"is\":{\"statusCode\":204}}]}"));
            assertNotNull(t.spaceListStubs(port, "flow-B"));
            assertNotNull(t.spaceRecorded(port, "flow-B"));
            t.spaceDelete(port, "flow-B");

            t.deleteImposter(port);
        }
    }

    @Test
    void adminPlaneAndDelegatedOps() throws Exception {
        try (EmbeddedTransport t = open()) {
            int port = portOf(t.createImposter(recordingImposter()));

            URI admin = t.adminUri();
            assertNotNull(admin, "adminUri lazily starts the in-process admin server");
            HttpResponse<String> health = HTTP.send(
                    HttpRequest.newBuilder(admin.resolve("/imposters")).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode(), "admin API is reachable");

            // delegated (no direct C symbol) — must route through the admin plane
            assertNotNull(t.getImposter(port), "getImposter via admin");
            assertNotNull(t.scenarios(port, Optional.empty()), "scenarios via admin");
            t.disable(port);
            t.enable(port);

            t.deleteImposter(port);
        }
    }

    @Test
    void invalidDefinitionIsMappedToRiftException() {
        try (EmbeddedTransport t = open()) {
            assertThrows(RiftException.class,
                    () -> t.createImposter(JsonValue.parse("{\"protocol\":\"not-a-protocol\"}")));
        }
    }

    @Test
    void useAfterCloseThrowsCleanlyNotNativeCrash() {
        EmbeddedTransport t = open();
        t.close();
        assertThrows(IllegalStateException.class, () -> t.recorded(1),
                "a downcall after close must fail as a Java exception, never a native crash");
        t.close(); // idempotent
    }

    @Test
    void repeatedCyclesStayStable() {
        // Arena/string ownership discipline: many create/record/delete cycles must not wedge or leak.
        try (EmbeddedTransport t = open()) {
            for (int i = 0; i < 200; i++) {
                int port = portOf(t.createImposter(recordingImposter()));
                t.recorded(port);
                t.deleteImposter(port);
            }
        }
    }
}
