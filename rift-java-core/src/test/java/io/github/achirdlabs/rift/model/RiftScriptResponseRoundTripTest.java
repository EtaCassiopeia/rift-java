package io.github.achirdlabs.rift.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Rift script-only response — no {@code is} block, the response body/status comes entirely from
 * the script — does not appear in any corpus fixture; hand-written spec-derived round-trip test
 * (G2 + G3).
 */
class RiftScriptResponseRoundTripTest {

    private static final String JSON = """
            {
              "predicates": [{"equals": {"path": "/computed"}}],
              "responses": [
                {
                  "_rift": {
                    "script": {"engine": "rhai", "code": "response.body = \\"computed: \\" + request.path;"},
                    "templated": true
                  }
                }
              ]
            }
            """;

    @Test
    void riftScriptResponseRoundTrips() {
        RoundTripAssertions.assertRoundTrips(JSON, Stub::fromJson, Stub::toJson);
    }

    @Test
    void riftScriptFieldsAreTyped() {
        Stub stub = Stub.fromJson(JSON);
        Response response = stub.responses().get(0);
        assertTrue(response instanceof Response.RiftScript);
        RiftResponseExtension rift = ((Response.RiftScript) response).rift();
        assertTrue(rift.templated());
        RiftScriptConfig script = rift.script().orElseThrow();
        assertEquals("rhai", script.engine().orElseThrow());
        assertEquals(1, script.sourceCount());
    }
}
