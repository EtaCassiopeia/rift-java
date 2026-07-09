package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public escape hatches ({@code Imposter.fromJson}/{@code toJson}, {@code
 * Stub.fromJson}/{@code toJson}) parse and round-trip happy-path input, and throw a typed codec
 * error — never a generic NullPointerException or ClassCastException — on malformed input.
 */
class EscapeHatchTest {

    @Test
    void imposterFromJsonHappyPath() {
        Imposter imposter = Imposter.fromJson("""
                {
                  "port": 5000,
                  "protocol": "http",
                  "name": "escape hatch demo",
                  "stubs": [
                    {"predicates": [{"equals": {"path": "/ping"}}], "responses": [{"is": {"statusCode": 200, "body": "pong"}}]}
                  ]
                }
                """);

        assertEquals("escape hatch demo", imposter.name().orElseThrow());
        assertEquals(1, imposter.stubs().size());
        assertTrue(imposter.toJson().contains("\"pong\""));
    }

    @Test
    void imposterFromJsonMalformedSyntaxThrowsTypedError() {
        // Unterminated object: a genuine JSON syntax error, not a shape problem.
        assertThrows(JsonParseException.class, () -> Imposter.fromJson("{ \"port\": 4545, "));
    }

    @Test
    void imposterFromJsonMalformedShapeThrowsTypedError() {
        // Well-formed JSON, but "port" must be a number, not a string.
        assertThrows(WireFormatException.class, () -> Imposter.fromJson("{\"port\": \"not-a-number\", \"stubs\": []}"));
    }

    @Test
    void stubFromJsonHappyPath() {
        Stub stub = Stub.fromJson("""
                {"predicates": [{"equals": {"path": "/x"}}], "responses": [{"is": {"statusCode": 204}}]}
                """);

        assertEquals(1, stub.predicates().size());
        assertEquals(1, stub.responses().size());
        Stub roundTripped = Stub.fromJson(stub.toJson());
        assertEquals(stub, roundTripped);
    }

    @Test
    void stubFromJsonMalformedSyntaxThrowsTypedError() {
        assertThrows(JsonParseException.class, () -> Stub.fromJson("{ this is not json "));
    }

    @Test
    void stubFromJsonMalformedShapeThrowsTypedError() {
        // A predicate object naming zero operation keys is a shape error, not a syntax error.
        assertThrows(WireFormatException.class,
                () -> Stub.fromJson("{\"predicates\": [{\"caseSensitive\": true}], \"responses\": []}"));
    }
}
