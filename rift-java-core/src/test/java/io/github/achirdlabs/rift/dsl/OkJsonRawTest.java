package io.github.achirdlabs.rift.dsl;

import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonString;
import io.github.achirdlabs.rift.model.IsResponse;
import io.github.achirdlabs.rift.model.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * {@code okJsonRaw} serves its payload verbatim (issue #96), where {@code okJson(String)} reparses
 * and canonicalizes. Uses a non-canonical input (odd whitespace and key order) so the two differ.
 */
class OkJsonRawTest {

    private static final String NON_CANONICAL = "{\"b\":1,   \"a\":2}";

    private static IsResponse buildIs(IsSpec spec) {
        return ((Response.Is) spec.build()).is();
    }

    @Test
    void okJsonRawServesBodyVerbatim() {
        IsResponse ir = buildIs(RiftDsl.okJsonRaw(NON_CANONICAL));
        assertEquals("200", ir.statusCode());
        assertEquals("application/json", ir.headers().get("Content-Type").get(0));
        // Body is the exact input string, not a reparsed object.
        assertEquals(new JsonString(NON_CANONICAL), ir.body().orElseThrow());
    }

    @Test
    void okJsonReparsesToAnObject() {
        IsResponse ir = buildIs(RiftDsl.okJson(NON_CANONICAL));
        // The verbatim string is gone: okJson parsed it into a JSON object.
        assertInstanceOf(JsonObject.class, ir.body().orElseThrow());
    }
}
