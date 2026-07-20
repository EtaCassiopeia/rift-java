package io.github.achirdlabs.rift.testcontainers;

import io.github.achirdlabs.rift.Imposter;
import io.github.achirdlabs.rift.Rift;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static io.github.achirdlabs.rift.dsl.RiftDsl.imposter;
import static io.github.achirdlabs.rift.dsl.RiftDsl.inMemoryFlowState;
import static io.github.achirdlabs.rift.dsl.RiftDsl.okJson;
import static io.github.achirdlabs.rift.dsl.RiftDsl.onGet;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Real-engine coverage for the correlated-isolation space path — {@code imposter.space(flowId)
 * .addStub(...)} → {@link io.github.achirdlabs.rift.transport.RemoteTransport#spaceAddStub}. Gated
 * on {@code RIFT_IT} (like the other {@code *IT} classes), so no container starts on the Docker-free
 * lanes.
 *
 * <p>Regression guard for #52: unlike {@code POST /imposters/:port/stubs} (which takes an {@code
 * {"stub":{...}}} envelope, see {@code addStub}), the space endpoint {@code POST /imposters/:port/
 * spaces/:flowId/stubs} deserializes a <em>bare</em> stub — so {@code spaceAddStub} must post the
 * stub un-enveloped. This exercises that against a real {@code rift-proxy} container; only in-JVM
 * fakes (never the real engine) covered it before, which is why the correct body shape was unverified.
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RIFT_IT", matches = "1|true")
class SpaceStubIT {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Container
    static final RiftContainer RIFT = new RiftContainer().withImposterPorts(4547);

    @Test
    void spaceScopedStubAddedAtRuntimeIsServedForItsFlow() throws Exception {
        try (Rift client = RIFT.client()) {
            Imposter imp = client.create(imposter("spaces")
                    .port(4547)
                    .flowState(inMemoryFlowState().flowIdFromHeader("X-Mock-Space")));

            // Dynamic, space-scoped stub via the admin API (spaceAddStub posts a bare stub).
            imp.space("alice").addStub(onGet("/who").willReturn(okJson("{\"owner\":\"alice\"}")));

            // AC2: the space lists exactly the stub we added (spaceAddStub -> spaceListStubs round-trip).
            assertEquals(1, imp.space("alice").stubs().size(), "the space-scoped stub was registered");

            // AC1: served for a request resolved to flow "alice".
            HttpResponse<String> alice = get(imp.uri() + "/who", "alice");
            assertEquals(200, alice.statusCode());
            assertEquals("{\"owner\":\"alice\"}", alice.body());

            // AC3: NOT served for a different flow — the stub is isolated to its space. An unmatched
            // request gets the engine's Mountebank-style empty fallback (200, empty body), never
            // alice's stub.
            HttpResponse<String> bob = get(imp.uri() + "/who", "bob");
            assertEquals(200, bob.statusCode());
            assertEquals("", bob.body(), "a space-scoped stub must not leak to another flow");
        }
    }

    private static HttpResponse<String> get(String url, String space) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                        .header("X-Mock-Space", space).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
