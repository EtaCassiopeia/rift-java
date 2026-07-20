package io.github.achirdlabs.rift.json;

/** A JSON boolean. */
public record JsonBool(boolean value) implements JsonValue {

    public static final JsonBool TRUE = new JsonBool(true);
    public static final JsonBool FALSE = new JsonBool(false);

    public static JsonBool of(boolean value) {
        return value ? TRUE : FALSE;
    }
}
