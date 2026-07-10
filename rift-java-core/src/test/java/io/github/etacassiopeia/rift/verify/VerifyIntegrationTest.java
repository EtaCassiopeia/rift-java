package io.github.etacassiopeia.rift.verify;

import com.sun.net.httpserver.HttpServer;
import io.github.etacassiopeia.rift.ConnectOptions;
import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.Rift;
import io.github.etacassiopeia.rift.VersionCheck;
import io.github.etacassiopeia.rift.error.InvalidDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.etacassiopeia.rift.dsl.RiftDsl.equalTo;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.onGet;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.onPost;
import static io.github.etacassiopeia.rift.verify.VerificationTimes.times;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** End-to-end verification behavior over a fake admin server: match counting, near-miss diffs, scoping. */
class VerifyIntegrationTest {

    private HttpServer server;
    private final Map<String, String> routes = new ConcurrentHashMap<>(); // "METHOD /path" -> JSON body
    private Rift rift;

    private static final String RECORDING_IMPOSTER = "{\"port\":4545,\"protocol\":\"http\",\"recordRequests\":true,\"stubs\":[]}";
    private static final String THREE_REQUESTS = "{\"requests\":["
            + "{\"method\":\"GET\",\"path\":\"/api/users/2\"},"
            + "{\"method\":\"POST\",\"path\":\"/api/users/1\"},"
            + "{\"method\":\"GET\",\"path\":\"/health\"}]}";

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
            String body = routes.getOrDefault(key, "{}");
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            try (var os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        // an imposter that records; three recorded requests
        routes.put("GET /imposters/4545", RECORDING_IMPOSTER);
        routes.put("GET /imposters/4545/savedRequests", THREE_REQUESTS);
        rift = Rift.connect(ConnectOptions.builder(
                java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()))
                .versionCheck(VersionCheck.OFF).build());
    }

    @AfterEach
    void tearDown() {
        rift.close();
        server.stop(0);
    }

    private Imposter recordingImposter() {
        return rift.imposter(4545).orElseThrow();
    }

    @Test
    void verifyPassesWhenCountMatches() {
        assertDoesNotThrow(() -> recordingImposter().verify(onGet("/api/users/2"), times(1)));
        assertDoesNotThrow(() -> recordingImposter().verify(onGet("/health")));  // default atLeastOnce
    }

    @Test
    void verifyFailsWithRankedNearMissDiff() {
        VerificationException ex = assertThrows(VerificationException.class,
                () -> recordingImposter().verify(onGet("/api/users/1"), times(1)));
        String msg = ex.getMessage();
        assertTrue(msg.contains("Verification failed"), msg);
        assertTrue(msg.contains("but was 0"), msg);
        assertTrue(msg.toLowerCase().contains("closest match"), msg);
        // the closest near-miss (same method GET, path differs) names the failing path clause
        assertTrue(msg.contains("/api/users/2"), msg);
        assertTrue(msg.contains("expected") && msg.contains("got"), msg);
    }

    @Test
    void verifyNoInteractions() {
        routes.put("GET /imposters/6000", "{\"port\":6000,\"recordRequests\":true,\"stubs\":[]}");
        routes.put("GET /imposters/6000/savedRequests", "{\"requests\":[]}");
        assertDoesNotThrow(() -> rift.imposter(6000).orElseThrow().verifyNoInteractions());
        assertThrows(VerificationException.class, () -> recordingImposter().verifyNoInteractions());
    }

    @Test
    void recordedWithMatchFilters() {
        List<io.github.etacassiopeia.rift.RecordedRequest> hits = recordingImposter().recorded(onGet("/health"));
        assertEquals(1, hits.size());
        assertEquals("/health", hits.get(0).path());
    }

    @Test
    void verifyOnNonRecordingImposterThrowsInvalidDefinition() {
        routes.put("GET /imposters/7000", "{\"port\":7000,\"protocol\":\"http\",\"stubs\":[]}"); // no recordRequests
        InvalidDefinition ex = assertThrows(InvalidDefinition.class,
                () -> rift.imposter(7000).orElseThrow().verify(onGet("/x")));
        assertTrue(ex.getMessage().toLowerCase().contains("record"), ex.getMessage());
    }

    @Test
    void spaceScopedVerifyIsIsolated() {
        routes.put("GET /imposters/4545/spaces/flow-A/recorded", "{\"requests\":[{\"method\":\"GET\",\"path\":\"/only-in-A\"}]}");
        routes.put("GET /imposters/4545/spaces/flow-B/recorded", "{\"requests\":[]}");
        assertDoesNotThrow(() -> recordingImposter().space("flow-A").verify(onGet("/only-in-A"), times(1)));
        assertThrows(VerificationException.class,
                () -> recordingImposter().space("flow-B").verify(onGet("/only-in-A"), times(1)));
    }

    @Test
    void verifyWithoutTimesFailsWhenZeroMatches() {
        // verify(match) with no times == atLeastOnce → fails when nothing matched
        assertThrows(VerificationException.class, () -> recordingImposter().verify(onGet("/never-called")));
    }

    @Test
    void nearMissRankingPutsFewerSatisfiedClausesLast() {
        routes.put("GET /imposters/8000", "{\"port\":8000,\"protocol\":\"http\",\"recordRequests\":true,\"stubs\":[]}");
        // match = 2 predicates: equals{method:GET,path:/x} and equals{headers:{H:v}}
        routes.put("GET /imposters/8000/savedRequests", "{\"requests\":["
                + "{\"method\":\"GET\",\"path\":\"/x\",\"headers\":{\"H\":\"wrong\"}},"    // 1 clause satisfied
                + "{\"method\":\"POST\",\"path\":\"/y\",\"headers\":{\"H\":\"wrong\"}}]}"); // 0 clauses satisfied
        VerificationException ex = assertThrows(VerificationException.class,
                () -> rift.imposter(8000).orElseThrow().verify(onGet("/x").withHeader("H", equalTo("v")), times(1)));
        String msg = ex.getMessage();
        int posGetX = msg.indexOf("/x");
        int posPostY = msg.indexOf("/y");
        assertTrue(posGetX >= 0 && posPostY >= 0 && posGetX < posPostY,
                "the 1-clause near-miss (GET /x) must be ranked before the 0-clause one (POST /y):\n" + msg);
    }

    @Test
    void nearMissDiffCapsAtTenLinesWithRemainderNote() {
        StringBuilder reqs = new StringBuilder("{\"requests\":[");
        for (int i = 0; i < 12; i++) {
            reqs.append(i == 0 ? "" : ",").append("{\"method\":\"GET\",\"path\":\"/miss/").append(i).append("\"}");
        }
        reqs.append("]}");
        routes.put("GET /imposters/8100", "{\"port\":8100,\"protocol\":\"http\",\"recordRequests\":true,\"stubs\":[]}");
        routes.put("GET /imposters/8100/savedRequests", reqs.toString());
        VerificationException ex = assertThrows(VerificationException.class,
                () -> rift.imposter(8100).orElseThrow().verify(onGet("/target"), times(1)));
        assertTrue(ex.getMessage().toLowerCase().contains("more"), "12 near-misses must be capped with a '… and k more' note:\n" + ex.getMessage());
    }

    @Test
    void spaceRecordedWithMatchFilters() {
        routes.put("GET /imposters/4545/spaces/flow-A/recorded",
                "{\"requests\":[{\"method\":\"GET\",\"path\":\"/a\"},{\"method\":\"GET\",\"path\":\"/b\"}]}");
        List<io.github.etacassiopeia.rift.RecordedRequest> hits = recordingImposter().space("flow-A").recorded(onGet("/a"));
        assertEquals(1, hits.size());
        assertEquals("/a", hits.get(0).path());
    }

    @Test
    void injectPredicateInVerifyThrowsInvalidDefinition() {
        assertThrows(InvalidDefinition.class,
                () -> recordingImposter().verify(onPost("/x").withPredicateInject("function(){return true;}")));
    }
}
