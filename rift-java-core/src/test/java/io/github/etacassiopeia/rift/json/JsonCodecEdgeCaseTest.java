package io.github.etacassiopeia.rift.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Edge cases of the hand-written JSON reader/writer not covered by {@link JsonValueTest}: unicode
 * and surrogate-pair escapes, duplicate-key last-wins semantics, a battery of malformed inputs that
 * must each throw {@link JsonParseException}, and {@link JsonValue#toPrettyJson()} formatting.
 */
class JsonCodecEdgeCaseTest {

    @Test
    void unicodeEscapeParsesToCharacter() {
        JsonValue value = JsonValue.parse("\"\\u00e9\"");
        assertEquals("é", ((JsonString) value).value());
    }

    @Test
    void surrogatePairEscapeParsesToSupplementaryCharacter() {
        // U+1F600 GRINNING FACE, encoded as a UTF-16 surrogate pair: 😀.
        JsonValue value = JsonValue.parse("\"\\ud83d\\ude00\"");
        String parsed = ((JsonString) value).value();
        assertEquals(1, parsed.codePointCount(0, parsed.length()));
        assertEquals(0x1F600, parsed.codePointAt(0));
    }

    @Test
    void duplicateKeysLastValueWins() {
        JsonValue value = JsonValue.parse("{\"a\":1,\"a\":2}");
        JsonObject obj = (JsonObject) value;
        assertEquals(1, obj.fields().size());
        assertEquals(2, ((JsonNumber) obj.get("a")).asInt());
    }

    @Test
    void trailingGarbageAfterDocumentThrows() {
        assertThrows(JsonParseException.class, () -> JsonValue.parse("1 2"));
    }

    @Test
    void unterminatedStringThrows() {
        assertThrows(JsonParseException.class, () -> JsonValue.parse("\"abc"));
    }

    @Test
    void unterminatedObjectThrows() {
        assertThrows(JsonParseException.class, () -> JsonValue.parse("{\"a\":1"));
    }

    @Test
    void invalidEscapeCharacterThrows() {
        assertThrows(JsonParseException.class, () -> JsonValue.parse("\"\\x\""));
    }

    @Test
    void leadingZeroNumberThrows() {
        assertThrows(JsonParseException.class, () -> JsonValue.parse("01"));
    }

    @Test
    void trailingDotWithNoFractionDigitThrows() {
        assertThrows(JsonParseException.class, () -> JsonValue.parse("1."));
    }

    @Test
    void danglingExponentThrows() {
        assertThrows(JsonParseException.class, () -> JsonValue.parse("1e"));
    }

    @Test
    void prettyJsonIsMultiLineAndReparsesEqual() {
        JsonValue value = JsonValue.parse("{\"a\":1,\"b\":[1,2]}");
        String pretty = value.toPrettyJson();
        assertTrue(pretty.contains("\n"), () -> "expected multi-line output, got: " + pretty);
        assertEquals(value, JsonValue.parse(pretty));
    }

    @Test
    void emptyObjectPrettyPrintsCompact() {
        assertEquals("{}", JsonObject.of().toPrettyJson());
    }

    @Test
    void emptyArrayPrettyPrintsCompact() {
        assertEquals("[]", JsonArray.of().toPrettyJson());
    }
}
