package io.github.etacassiopeia.rift.json;

/**
 * A JSON number, stored as its exact source text (not a {@code double}). Preserving the literal
 * digits — rather than round-tripping through a floating-point type — is what lets {@link
 * JsonWriter} echo a parsed number back byte-for-byte, which the round-trip gate (G2/G3) depends
 * on: reformatting {@code 3600} as {@code 3600.0} would count as a changed value.
 */
public record JsonNumber(String raw) implements JsonValue {

    public JsonNumber {
        if (!isValidJsonNumber(raw)) {
            throw new IllegalArgumentException("not a valid JSON number literal: " + raw);
        }
    }

    public static JsonNumber of(long value) {
        return new JsonNumber(Long.toString(value));
    }

    public static JsonNumber of(int value) {
        return new JsonNumber(Integer.toString(value));
    }

    public static JsonNumber of(double value) {
        return new JsonNumber(Double.toString(value));
    }

    public long asLong() {
        return Long.parseLong(raw);
    }

    public int asInt() {
        return Integer.parseInt(raw);
    }

    public double asDouble() {
        return Double.parseDouble(raw);
    }

    private static boolean isValidJsonNumber(String s) {
        // JSON grammar: -? (0|[1-9]\d*) (\.\d+)? ([eE][+-]?\d+)?
        return s != null && s.matches("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?");
    }
}
