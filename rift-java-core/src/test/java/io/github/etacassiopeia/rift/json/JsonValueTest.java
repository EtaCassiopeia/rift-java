package io.github.etacassiopeia.rift.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Direct tests of the hand-written JSON reader/writer and value equality. */
class JsonValueTest {

    @Test
    void parsesAllSixValueKinds() {
        JsonValue value = JsonValue.parse("""
                {"s": "text", "n": 42, "f": -1.5e2, "b": true, "nil": null, "arr": [1, 2, 3]}
                """);
        JsonObject obj = (JsonObject) value;
        assertEquals(new JsonString("text"), obj.get("s"));
        assertEquals(JsonNumber.of(42), obj.get("n"));
        assertEquals("-1.5e2", ((JsonNumber) obj.get("f")).raw());
        assertEquals(JsonBool.TRUE, obj.get("b"));
        assertEquals(JsonNull.INSTANCE, obj.get("nil"));
        assertEquals(new JsonArray(List.of(JsonNumber.of(1), JsonNumber.of(2), JsonNumber.of(3))), obj.get("arr"));
    }

    @Test
    void compactRoundTripPreservesNumberLiteralText() {
        // 3600 must come back as "3600", not reformatted through a double as "3600.0".
        String text = "{\"expiresIn\":3600}";
        assertEquals(text, JsonValue.parse(text).toJson());
    }

    @Test
    void objectEqualityIsOrderIndependent() {
        JsonValue a = JsonValue.parse("{\"a\":1,\"b\":2}");
        JsonValue b = JsonValue.parse("{\"b\":2,\"a\":1}");
        assertEquals(a, b);
    }

    @Test
    void writerPreservesInsertionOrderRegardlessOfEquality() {
        JsonObject obj = JsonObject.builder().put("z", new JsonString("1")).put("a", new JsonString("2")).build();
        assertEquals("{\"z\":\"1\",\"a\":\"2\"}", obj.toJson());
    }

    @Test
    void malformedJsonThrowsWithLineAndColumn() {
        JsonParseException ex = assertThrows(JsonParseException.class, () -> JsonValue.parse("{\"a\": }"));
        assertEquals(1, ex.line());
        assertTrue(ex.column() > 1);
    }

    @Test
    void trailingContentIsRejected() {
        assertThrows(JsonParseException.class, () -> JsonValue.parse("{} garbage"));
    }

    @Test
    void semanticEqualsToleratesStatusCodeNumberVsString() {
        JsonValue a = JsonValue.parse("{\"statusCode\": 200}");
        JsonValue b = JsonValue.parse("{\"statusCode\": \"200\"}");
        assertTrue(JsonValue.semanticEquals(a, b));
    }

    @Test
    void semanticEqualsRejectsStatusCodeMismatch() {
        JsonValue a = JsonValue.parse("{\"statusCode\": 200}");
        JsonValue b = JsonValue.parse("{\"statusCode\": 404}");
        assertFalse(JsonValue.semanticEquals(a, b));
    }

    @Test
    void semanticEqualsToleratesSingleHeaderValueVsOneElementArray() {
        JsonValue a = JsonValue.parse("{\"headers\": {\"X-One\": \"v\"}}");
        JsonValue b = JsonValue.parse("{\"headers\": {\"X-One\": [\"v\"]}}");
        assertTrue(JsonValue.semanticEquals(a, b));
    }

    @Test
    void semanticEqualsRejectsHeaderValueMismatch() {
        JsonValue a = JsonValue.parse("{\"headers\": {\"X-One\": \"v\"}}");
        JsonValue b = JsonValue.parse("{\"headers\": {\"X-One\": [\"v\", \"w\"]}}");
        assertFalse(JsonValue.semanticEquals(a, b));
    }

    @Test
    void semanticEqualsIgnoresKeyOrderEverywhere() {
        JsonValue a = JsonValue.parse("{\"outer\": {\"a\": 1, \"b\": 2}}");
        JsonValue b = JsonValue.parse("{\"outer\": {\"b\": 2, \"a\": 1}}");
        assertTrue(JsonValue.semanticEquals(a, b));
    }

    @Test
    void semanticEqualsDetectsDroppedAndAddedKeys() {
        JsonValue a = JsonValue.parse("{\"a\": 1, \"b\": 2}");
        JsonValue b = JsonValue.parse("{\"a\": 1}");
        assertFalse(JsonValue.semanticEquals(a, b));
        assertFalse(JsonValue.semanticEquals(b, a));
    }

    @Test
    void jsonObjectValueEqualityIgnoresInsertionOrder() {
        JsonObject a = JsonObject.builder().put("x", JsonBool.TRUE).put("y", JsonBool.FALSE).build();
        JsonObject b = JsonObject.builder().put("y", JsonBool.FALSE).put("x", JsonBool.TRUE).build();
        assertEquals(a, b);
        assertEquals(Map.of("x", JsonBool.TRUE, "y", JsonBool.FALSE), a.fields());
    }
}
