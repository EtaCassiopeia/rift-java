package io.github.achirdlabs.rift.conformance;

import io.github.achirdlabs.rift.Imposter;
import io.github.achirdlabs.rift.RecordedRequest;
import io.github.achirdlabs.rift.Rift;
import io.github.achirdlabs.rift.SpawnOptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static io.github.achirdlabs.rift.dsl.RiftDsl.imposter;
import static io.github.achirdlabs.rift.dsl.RiftDsl.inMemoryFlowState;
import static io.github.achirdlabs.rift.dsl.RiftDsl.okJson;
import static io.github.achirdlabs.rift.dsl.RiftDsl.onGet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code Space.recorded()} against a real engine — the coverage whose absence let it ship calling a
 * route that does not exist.
 *
 * <p>The method spent its whole life green: the only tests that touched it were fakes told to serve
 * the invented {@code spaces/{flowId}/recorded} path, and a fake can only confirm what you told it.
 * Nothing here can pass without the engine agreeing, which is the entire point — it is the same
 * lesson {@code SpaceStubIT} records for #52.
 *
 * <p>Gated to {@link ConformanceTransport#SPAWN}: this asserts an admin-API route, which the
 * in-process FFI transport does not serve. Needs no corpus, just {@code RIFT_IT=1}.
 */
class SpaceRecordedIT {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final int PORT = 4596;
    private static final String FLOW_HEADER = "X-Flow-Id";

    @TestFactory
    Stream<DynamicTest> aSpaceReadsOnlyItsOwnTraffic() {
        return gated("space(flowId).recorded() over a real engine", () -> {
            try (Rift rift = Rift.spawn(SpawnOptions.builder().build())) {
                Imposter imp = rift.create(imposter("space-recorded")
                        .port(PORT)
                        .protocol("http")
                        .record()
                        .flowState(inMemoryFlowState().flowIdFromHeader(FLOW_HEADER))
                        .stub(onGet("/a").willReturn(okJson("{\"ok\":true}"))));

                get(imp.uri() + "/alice-1", "alice");
                get(imp.uri() + "/bob-1", "bob");
                get(imp.uri() + "/alice-2", "alice");

                // The assertion that was impossible before: a 404 route cannot return alice's traffic.
                assertEquals(List.of("/alice-1", "/alice-2"),
                        imp.space("alice").recorded().stream().map(RecordedRequest::path).toList(),
                        "alice's space sees alice's requests");
                assertEquals(List.of("/bob-1"),
                        imp.space("bob").recorded().stream().map(RecordedRequest::path).toList(),
                        "and bob's sees only bob's");

                // The imposter-wide journal still has everything — the space is a view, not a split.
                assertEquals(3, imp.recorded().size());

                // A flow nobody used is empty, not an error.
                assertEquals(List.of(), imp.space("nobody").recorded());
            }
        });
    }

    private static void get(String url, String flowId) throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                        .header(FLOW_HEADER, flowId).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        // Unmatched paths still record (the engine serves Mountebank's empty fallback); the journal,
        // not the stub, is under test.
        assertEquals(200, response.statusCode(), "the request must be served, so it is recorded");
    }

    /** Reports the two skip conditions separately so a lane that silently lost RIFT_IT is diagnosable. */
    private static Stream<DynamicTest> gated(String name, Executable body) {
        return Stream.of(DynamicTest.dynamicTest(name, () -> {
            assumeTrue(integrationEnabled(), "set RIFT_IT=1 to run the live-engine space lane");
            assumeTrue(ConformanceTransport.selected() == ConformanceTransport.SPAWN,
                    "this asserts an admin-API route; the FFI transport does not serve one — SPAWN lane only");
            body.run();
        }));
    }

    @FunctionalInterface
    private interface Executable {
        void run() throws Exception;
    }

    private static boolean integrationEnabled() {
        String it = System.getenv("RIFT_IT");
        return it != null && !it.isBlank() && !it.equals("0");
    }
}
