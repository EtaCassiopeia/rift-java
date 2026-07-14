package io.github.etacassiopeia.rift.testcontainers;

import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.Rift;
import io.github.etacassiopeia.rift.verify.ClosestMiss;
import io.github.etacassiopeia.rift.verify.VerificationException;
import io.github.etacassiopeia.rift.verify.VerificationResult;
import io.github.etacassiopeia.rift.verify.VerifyDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static io.github.etacassiopeia.rift.dsl.RiftDsl.imposter;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.okJson;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.onGet;
import static io.github.etacassiopeia.rift.verify.VerificationTimes.times;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-engine coverage for verification (#127). Since verification defers to the engine, every
 * other suite necessarily asserts against a canned verdict — a fake transport or a fake admin
 * server cannot evaluate a predicate. This is the only place the SDK's counts, {@code closest}
 * mapping and {@code satisfied} arithmetic are checked against verdicts a real engine actually
 * produced from real traffic, so it is what proves the record shapes match the live wire format.
 *
 * <p>Gated on {@code RIFT_IT} like its siblings: no container starts unless it is set.
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RIFT_IT", matches = "1|true")
class VerifyEngineIT {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Container
    static final RiftContainer RIFT = new RiftContainer().withImposterPorts(4545);

    /** An imposter that records, with three requests driven through it: two GET /a, one GET /b. */
    private Imposter recordingImposterWithTraffic(Rift client) throws Exception {
        // The container outlives each test, so drop a previous run's imposter (and its journal)
        // rather than colliding on the port or counting stale traffic.
        client.imposter(4545).ifPresent(Imposter::delete);
        Imposter users = client.create(imposter("verify-target").port(4545).record()
                .stub(onGet("/a").willReturn(okJson("{\"hit\":\"a\"}")))
                .stub(onGet("/b").willReturn(okJson("{\"hit\":\"b\"}"))));
        get(users.uri() + "/a");
        get(users.uri() + "/a");
        get(users.uri() + "/b");
        return users;
    }

    @Test
    void countsComeFromTheEnginesOwnEvaluator() throws Exception {
        try (Rift client = RIFT.client()) {
            Imposter users = recordingImposterWithTraffic(client);

            VerificationResult a = users.verifyResult(onGet("/a"));
            assertEquals(2, a.matched(), "the engine counted both GET /a");
            assertEquals(3, a.total(), "total is all recorded traffic, matched or not");
            assertTrue(a.satisfied(), "2 satisfies the default atLeast(1)");

            assertEquals(0, users.verifyResult(onGet("/never")).matched(), "an unmatched path counts zero");
            assertFalse(users.verifyResult(onGet("/never")).satisfied());
        }
    }

    @Test
    void verifyAgreesWithTheEngineVerdict() throws Exception {
        try (Rift client = RIFT.client()) {
            Imposter users = recordingImposterWithTraffic(client);

            assertDoesNotThrow(() -> users.verify(onGet("/a"), times(2)));
            assertDoesNotThrow(() -> users.verify(onGet("/b")));
            assertThrows(VerificationException.class, () -> users.verify(onGet("/a"), times(1)),
                    "exactly-1 must fail against the engine's count of 2");
        }
    }

    @Test
    void requestsDetailReturnsTheMatchedTrafficItself() throws Exception {
        try (Rift client = RIFT.client()) {
            Imposter users = recordingImposterWithTraffic(client);

            VerificationResult withRequests = users.verifyResult(onGet("/a"), VerifyDetail.REQUESTS);
            assertEquals(2, withRequests.requests().size(), "REQUESTS returns the matched requests");
            assertTrue(withRequests.requests().stream().allMatch(r -> r.path().equals("/a")));

            assertTrue(users.verifyResult(onGet("/a")).requests().isEmpty(),
                    "without the flag the engine omits them and the journal is not shipped");
        }
    }

    /**
     * The mapping that only a real engine can prove: {@code closest.failedPredicates[].predicate}
     * must survive the engine's own serialization back into a typed {@link io.github.etacassiopeia.rift.model.Predicate}.
     */
    @Test
    void closestMissIsScoredAndExplainedByTheEngine() throws Exception {
        try (Rift client = RIFT.client()) {
            Imposter users = recordingImposterWithTraffic(client);

            VerificationResult miss = users.verifyResult(onGet("/nope"), times(1), VerifyDetail.CLOSEST);
            assertFalse(miss.satisfied());
            assertEquals(0, miss.matched());

            ClosestMiss closest = miss.closest().orElseThrow(() -> new AssertionError(
                    "the engine returns a closest non-match when traffic exists and CLOSEST is asked for"));
            assertFalse(closest.failedPredicates().isEmpty(), "the engine names the clauses that failed");
            assertTrue(closest.failedPredicates().stream()
                            .anyMatch(f -> f.predicate().toJson().contains("/nope")),
                    "the failed clause round-trips back into a typed Predicate: "
                            + closest.failedPredicates());
        }
    }

    @Test
    void verificationExceptionCarriesTheEnginesResult() throws Exception {
        try (Rift client = RIFT.client()) {
            Imposter users = recordingImposterWithTraffic(client);

            VerificationException ex = assertThrows(VerificationException.class,
                    () -> users.verify(onGet("/a"), times(99)));

            VerificationResult carried = ex.result().orElseThrow();
            assertEquals(2, carried.matched(), "the engine's real count rides on the exception");
            assertEquals(3, carried.total());
        }
    }

    private static HttpResponse<String> get(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
