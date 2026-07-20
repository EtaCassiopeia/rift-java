package io.github.achirdlabs.rift.json;

/** The JSON {@code null} literal. A record with no components, so all instances are equal. */
public record JsonNull() implements JsonValue {

    public static final JsonNull INSTANCE = new JsonNull();
}
