package io.github.etacassiopeia.rift.transport;

import io.github.etacassiopeia.rift.json.JsonValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

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
        transport.startIntercept(JsonValue.parse("{\"port\":0}"));
        assertTrue(sawRequest("POST", "/intercept"));
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
