package io.github.etacassiopeia.rift.json;

/**
 * A parsed JSON value. Every JSON document reduces to exactly one of these six shapes — there is
 * no generic "unknown" fallback, so a parse either produces one of these or throws {@link
 * JsonParseException}.
 */
public sealed interface JsonValue
        permits JsonObject, JsonArray, JsonString, JsonNumber, JsonBool, JsonNull {

    /** Parses {@code text} into a {@link JsonValue} tree. */
    static JsonValue parse(String text) {
        return new JsonReader(text).parseDocument();
    }

    /** Serializes this value to compact JSON text (object keys keep insertion order). */
    default String toJson() {
        return JsonWriter.compact(this);
    }

    /** Serializes this value to indented JSON text. */
    default String toPrettyJson() {
        return JsonWriter.pretty(this);
    }

    /**
     * Structural equality tolerant of exactly the normalizations the wire codec legitimately
     * applies: object key order is ignored everywhere; a {@code statusCode} field compares its
     * number and string forms equal (200 == "200"); inside a {@code headers} object, a single
     * string value compares equal to a one-element array holding that same string. Everything
     * else must match exactly — no dropped/added keys, no changed values, no changed types.
     */
    static boolean semanticEquals(JsonValue a, JsonValue b) {
        return SemanticEquals.equals(a, b, null);
    }
}
