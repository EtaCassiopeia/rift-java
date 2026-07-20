package io.github.achirdlabs.rift.transport;

import io.github.achirdlabs.rift.json.JsonArray;
import io.github.achirdlabs.rift.json.JsonNumber;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import io.github.achirdlabs.rift.error.CommunicationError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #65 — the 3 new {@link RiftTransport} SPI methods over the remote (HTTP) transport. */
class RemoteV2AdminTest {

    private static RemoteTransport transport(FakeAdminServer s) {
        return new RemoteTransport(s.baseUri(), Optional.empty(), Duration.ofSeconds(5));
    }

    private static boolean hit(FakeAdminServer s, String method, String path) {
        return s.received().stream().anyMatch(r -> r.method().equals(method) && r.path().equals(path));
    }

    @Test
    void getStubHitsTheStubRoute() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("GET /imposters/4545/stubs/0", 200, "{\"responses\":[{\"is\":{\"statusCode\":200}}]}");
            try (RemoteTransport t = transport(s)) {
                assertInstanceOf(JsonObject.class, t.getStub(4545, new StubAddress.ByIndex(0)));
                assertTrue(hit(s, "GET", "/imposters/4545/stubs/0"));
            }
        }
    }

    @Test
    void verifyPostsToTheVerifyRoute() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("POST /imposters/4545/verify", 200, "{\"matched\":1,\"total\":2}");
            try (RemoteTransport t = transport(s)) {
                JsonValue v = t.verify(4545, JsonValue.parse("{\"predicates\":[]}"));
                assertEquals(1, ((JsonNumber) ((JsonObject) v).get("matched")).asInt());
                assertTrue(hit(s, "POST", "/imposters/4545/verify"));
            }
        }
    }

    @Test
    void stubWarningsExtractsRiftWarnings() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("GET /imposters/4545", 200,
                    "{\"port\":4545,\"protocol\":\"http\",\"stubs\":[],\"_rift\":{\"warnings\":[{\"kind\":\"shadowed\"}]}}");
            try (RemoteTransport t = transport(s)) {
                JsonValue w = t.stubWarnings(4545);
                assertEquals(1, assertInstanceOf(JsonArray.class, w).items().size());
            }
        }
    }

    @Test
    void stubWarningsEmptyWhenAbsent() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("GET /imposters/4545", 200, "{\"port\":4545,\"protocol\":\"http\",\"stubs\":[]}");
            try (RemoteTransport t = transport(s)) {
                assertTrue(assertInstanceOf(JsonArray.class, t.stubWarnings(4545)).items().isEmpty(),
                        "absent _rift.warnings → empty array");
            }
        }
    }

    @Test
    void stubWarningsMalformedShapeSurfaces() {
        // A present-but-wrong-typed _rift.warnings (version skew / engine bug) must not masquerade as
        // "no warnings" — it surfaces as a CommunicationError.
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("GET /imposters/4545", 200,
                    "{\"port\":4545,\"protocol\":\"http\",\"stubs\":[],\"_rift\":{\"warnings\":\"shadowed\"}}");
            try (RemoteTransport t = transport(s)) {
                assertThrows(CommunicationError.class, () -> t.stubWarnings(4545));
            }
        }
    }

    @Test
    void positionalAddStubSendsTheIndexInTheBody() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("POST /imposters/4545/stubs", 200, "");
            try (RemoteTransport t = transport(s)) {
                t.addStub(4545, JsonValue.parse("{\"responses\":[{\"is\":{\"statusCode\":418}}]}"), 2);
                String body = s.received().stream()
                        .filter(r -> r.method().equals("POST") && r.path().equals("/imposters/4545/stubs"))
                        .findFirst().orElseThrow().body();
                assertTrue(body.contains("\"index\":2") || body.contains("\"index\": 2"), body);
                assertTrue(body.contains("\"stub\""), "still wraps the stub envelope: " + body);
            }
        }
    }
}
