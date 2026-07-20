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
import static io.github.achirdlabs.rift.dsl.RiftDsl.okJson;
import static io.github.achirdlabs.rift.dsl.RiftDsl.onGet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end round-trip against a REAL {@code rift-proxy} container. Gated on {@code RIFT_IT} (like
 * rift-conformance): the class is disabled — and so no container starts — unless {@code RIFT_IT}
 * is set, keeping the Docker-free CI lanes green. Runs on the Docker-enabled CI lane and locally
 * with {@code RIFT_IT=1}.
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RIFT_IT", matches = "1|true")
class RiftContainerIT {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Container
    static final RiftContainer FIXED_PORT = new RiftContainer().withImposterPorts(4545);

    @Container
    static final RiftContainer GATEWAY = new RiftContainer().withGateway();

    @Container
    static final RiftContainer SECURED = new RiftContainer().withApiKey("s3cret").withImposterPorts(4546);

    // AC1: an imposter on a pre-exposed fixed port is reachable through Docker's port remapping,
    // and imposter.uri() already carries the mapped host:port — zero user-side remapping.
    @Test
    void fixedPortImposterReachableThroughPortMapping() throws Exception {
        try (Rift client = FIXED_PORT.client()) {
            Imposter users = client.create(imposter("users").port(4545)
                    .stub(onGet("/u/1").willReturn(okJson("{\"id\":1}"))));

            HttpResponse<String> resp = get(users.uri() + "/u/1");
            assertEquals(200, resp.statusCode());
            assertEquals("{\"id\":1}", resp.body());
        }
    }

    // AC2: gateway mode needs no pre-exposed port — traffic routes via {admin}/__rift/:port, and
    // imposter.uri() carries that prefix.
    @Test
    void gatewayModeRoutesThroughAdminPort() throws Exception {
        try (Rift client = GATEWAY.client()) {
            Imposter users = client.create(imposter("users").port(4545)
                    .stub(onGet("/u/1").willReturn(okJson("{\"id\":1}"))));

            URI uri = users.uri();
            assertTrue(uri.toString().contains("/__rift/4545"), "gateway uri carries the prefix: " + uri);
            HttpResponse<String> resp = get(uri + "/u/1");
            assertEquals(200, resp.statusCode());
            assertEquals("{\"id\":1}", resp.body());
        }
    }

    // AC4 wiring (end-to-end): withApiKey gates the admin control plane, and client() authenticates
    // with the same key so admin operations succeed. The imposter data plane is not key-gated.
    @Test
    void apiKeyGatesAdminAndClientAuthenticates() throws Exception {
        HttpResponse<String> unauthenticated = get(SECURED.adminUri() + "/imposters");
        assertEquals(401, unauthenticated.statusCode(), "admin control plane rejects the missing key");

        try (Rift client = SECURED.client()) {
            Imposter users = client.create(imposter("users").port(4546)
                    .stub(onGet("/u/1").willReturn(okJson("{\"id\":1}"))));
            assertEquals(200, get(users.uri() + "/u/1").statusCode(), "client authenticated and created the imposter");
        }
    }

    private static HttpResponse<String> get(String url) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
