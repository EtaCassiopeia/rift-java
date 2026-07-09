package io.github.etacassiopeia.rift.json;

/** A JSON string value. */
public record JsonString(String value) implements JsonValue {

    public JsonString {
        java.util.Objects.requireNonNull(value, "value");
    }
}
