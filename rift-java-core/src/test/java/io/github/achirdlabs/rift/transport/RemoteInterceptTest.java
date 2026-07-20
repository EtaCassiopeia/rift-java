package io.github.achirdlabs.rift.transport;

import io.github.achirdlabs.rift.ConnectOptions;
import io.github.achirdlabs.rift.Intercept;
import io.github.achirdlabs.rift.InterceptOptions;
import io.github.achirdlabs.rift.Rift;
import io.github.achirdlabs.rift.VersionCheck;
import io.github.achirdlabs.rift.json.JsonValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link RemoteTransport} maps the intercept operations onto the admin API's {@code /intercept/*} routes. */
class RemoteInterceptTest {

    private FakeAdminServer server;
    private RemoteTransport transport;

    @BeforeEach
    void setUp() {
        server = new FakeAdminServer();
        server.respond("POST /intercept", 200, "{\"interceptPort\":9000,\"interceptUrl\":\"http://127.0.0.1:9000\"}");
        server.respond("POST /intercept/rules", 200, "{}");
        server.respond("GET /intercept/rules", 200, "[]");
        server.respond("DELETE /intercept/rules", 200, "{}");
        server.respond("GET /intercept/ca.pem", 200, "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----");
        transport = new RemoteTransport(server.baseUri(), Optional.empty(), Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        transport.close();
        server.close();
    }

    private boolean sawRequest(String method, String path) {
        return server.received().stream().anyMatch(r -> r.method().equals(method) && r.path().equals(path));
    }

    @Test
    void startInterceptPostsToIntercept() {
        // rift >= 0.13.3 grew a real POST /intercept start route (#493); the transport uses it.
        transport.startIntercept(JsonValue.parse("{\"port\":0}"));
        assertTrue(sawRequest("POST", "/intercept"));
    }

    @Test
    void attachProbesTheListenerAndBindsToTheEndpoint() {
        try (Rift rift = Rift.connect(
                ConnectOptions.builder(server.baseUri()).versionCheck(VersionCheck.OFF).build())) {
            Intercept intercept = rift.intercept(InterceptOptions.attach("127.0.0.1", 9443));
            // Attach probes the existing listener (GET /intercept/rules), never POST /intercept.
            assertTrue(sawRequest("GET", "/intercept/rules"), "attach probes the running listener");
            assertTrue(server.received().stream().noneMatch(r -> r.path().equals("/intercept")),
                    "attach must not attempt to start a listener");
            assertEquals(9443, intercept.address().getPort());
            assertEquals("127.0.0.1", intercept.address().getHostString());
        }
    }

    @Test
    void addRulesPostsToInterceptRules() {
        transport.interceptAddRules(JsonValue.parse("{\"host\":\"a.example\",\"serve\":{\"statusCode\":200}}"));
        assertTrue(sawRequest("POST", "/intercept/rules"));
    }

    @Test
    void listRulesGetsInterceptRules() {
        transport.interceptListRules();
        assertTrue(sawRequest("GET", "/intercept/rules"));
    }

    @Test
    void clearRulesDeletesInterceptRules() {
        transport.interceptClearRules();
        assertTrue(sawRequest("DELETE", "/intercept/rules"));
    }

    @Test
    void caPemGetsInterceptCaPem() {
        String pem = transport.interceptCaPem();
        assertTrue(pem.contains("BEGIN CERTIFICATE"), pem);
        assertTrue(sawRequest("GET", "/intercept/ca.pem"));
    }
}
