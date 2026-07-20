package io.github.achirdlabs.rift.json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A JSON object. {@code fields} preserves insertion order (a {@link LinkedHashMap}) so {@link
 * JsonWriter} emits keys deterministically; {@code equals}/{@code hashCode} come from {@link
 * java.util.AbstractMap}, which is order-independent — two objects with the same entries in a
 * different order are equal, matching JSON's own semantics.
 */
public record JsonObject(Map<String, JsonValue> fields) implements JsonValue {

    public JsonObject {
        fields = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public static JsonObject of() {
        return new JsonObject(Map.of());
    }

    public static JsonObject.Builder builder() {
        return new Builder();
    }

    public JsonValue get(String key) {
        return fields.get(key);
    }

    public boolean has(String key) {
        return fields.containsKey(key);
    }

    /** Ordered, mutable builder used by codec code to assemble an object field-by-field. */
    public static final class Builder {
        private final LinkedHashMap<String, JsonValue> fields = new LinkedHashMap<>();

        public Builder put(String key, JsonValue value) {
            fields.put(key, value);
            return this;
        }

        /** Puts {@code value} only when present, matching {@code skip_serializing_if} fields. */
        public Builder putIfPresent(String key, java.util.Optional<? extends JsonValue> value) {
            value.ifPresent(v -> fields.put(key, v));
            return this;
        }

        public JsonObject build() {
            return new JsonObject(fields);
        }
    }
}
