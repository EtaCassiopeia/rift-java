package io.github.achirdlabs.rift.model;

import io.github.achirdlabs.rift.json.JsonNumber;
import io.github.achirdlabs.rift.json.JsonString;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unknown/future wire keys at the four aggregate positions (imposter, stub, {@code is} response,
 * {@code proxy} response) survive a parse → serialize round-trip instead of being silently dropped
 * — required for corpus replay of real engine output and forward-compat with engine additions.
 */
class UnknownFieldPreservationTest {

    @Test
    void unknownKeysSurviveAtAllFourLevels() {
        String json = """
                {
                  "port": 4545,
                  "protocol": "http",
                  "name": "fidelity",
                  "futureImposterField": {"nested": 1},
                  "stubs": [
                    {
                      "predicates": [{"equals": {"path": "/x"}}],
                      "responses": [
                        {"is": {"statusCode": 200, "body": "ok", "futureIsField": [1, 2]}},
                        {"proxy": {"to": "http://up", "mode": "proxyOnce", "futureProxyField": "keep"}}
                      ],
                      "futureStubField": true
                    }
                  ]
                }
                """;

        ImposterDefinition def = ImposterDefinition.fromJson(json);

        // The unknown keys land in the typed `extra` maps at each level on read.
        assertTrue(def.extra().containsKey("futureImposterField"));
        Stub stub = def.stubs().get(0);
        assertTrue(stub.extra().containsKey("futureStubField"));
        Response.Is is = (Response.Is) stub.responses().get(0);
        assertTrue(is.is().extra().containsKey("futureIsField"));
        Response.Proxy proxy = (Response.Proxy) stub.responses().get(1);
        assertTrue(proxy.proxy().extra().containsKey("futureProxyField"));

        // And they survive a full parse -> serialize -> parse cycle at every level (write emits them,
        // read re-captures them). Per-key assertions, not a whole-tree compare: this test's concern is
        // unknown-key survival, independent of the modeled fields' serialization.
        ImposterDefinition reparsed = ImposterDefinition.fromJson(def.toJson());
        assertTrue(reparsed.extra().containsKey("futureImposterField"));
        Stub reStub = reparsed.stubs().get(0);
        assertTrue(reStub.extra().containsKey("futureStubField"));
        assertEquals(true, ((io.github.achirdlabs.rift.json.JsonBool) reStub.extra().get("futureStubField")).value());
        IsResponse reIs = ((Response.Is) reStub.responses().get(0)).is();
        assertTrue(reIs.extra().containsKey("futureIsField"));
        ProxyResponse reProxy = ((Response.Proxy) reStub.responses().get(1)).proxy();
        assertEquals("keep", ((JsonString) reProxy.extra().get("futureProxyField")).value());
    }

    @Test
    void withExtraEmitsKeyAndReparses() {
        ImposterDefinition def = ImposterDefinition.fromJson("""
                {"port": 4545, "protocol": "http", "stubs": []}
                """).withExtra("x-custom", new JsonString("v"));

        assertEquals("v", ((JsonString) def.extra().get("x-custom")).value());
        ImposterDefinition reparsed = ImposterDefinition.fromJson(def.toJson());
        assertTrue(reparsed.extra().containsKey("x-custom"));
    }

    @Test
    void constructorRejectsModeledKeyInExtra() {
        // A modeled key sneaking into `extra` is a precedence ambiguity — reject it at construction.
        assertThrows(WireFormatException.class,
                () -> new IsResponse("200", Map.of(), Optional.empty(), ResponseMode.TEXT,
                        Map.of("statusCode", JsonNumber.of(500))));
    }

    @Test
    void withExtraRejectsModeledKey() {
        ImposterDefinition def = ImposterDefinition.fromJson("""
                {"port": 4545, "protocol": "http", "stubs": []}
                """);
        assertThrows(WireFormatException.class, () -> def.withExtra("port", JsonNumber.of(1)));
    }

    @Test
    void withExtraAndRejectionCoverStubAndProxy() {
        // Each record has its own hand-written withExtra + MODELED_KEYS; cover Stub and ProxyResponse
        // directly (ImposterDefinition/IsResponse are covered above) so a copy-paste slip is caught.
        Stub stub = Stub.fromJson("{\"predicates\": [], \"responses\": []}").withExtra("x-stub", new JsonString("s"));
        assertEquals("s", ((JsonString) Stub.fromJson(stub.toJson()).extra().get("x-stub")).value());
        assertThrows(WireFormatException.class, () -> stub.withExtra("predicates", JsonNumber.of(1)));

        ProxyResponse proxy = new ProxyResponse("http://up").withExtra("x-proxy", new JsonString("p"));
        assertEquals("p", ((JsonString) proxy.extra().get("x-proxy")).value());
        assertThrows(WireFormatException.class, () -> proxy.withExtra("to", new JsonString("y")));
    }

    @Test
    void defaultResponsePositionAlsoPreservesExtra() {
        ImposterDefinition def = ImposterDefinition.fromJson("""
                {"port": 4545, "protocol": "http", "stubs": [],
                 "defaultResponse": {"statusCode": 404, "futureDefaultKey": "keep"}}
                """);
        assertTrue(def.defaultResponse().orElseThrow().extra().containsKey("futureDefaultKey"));
        ImposterDefinition reparsed = ImposterDefinition.fromJson(def.toJson());
        assertEquals("keep",
                ((JsonString) reparsed.defaultResponse().orElseThrow().extra().get("futureDefaultKey")).value());
    }
}
