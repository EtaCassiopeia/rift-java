package io.github.etacassiopeia.rift.json;

import java.util.ArrayList;
import java.util.List;

/** A JSON array. {@code items} is order-sensitive, matching JSON array semantics. */
public record JsonArray(List<JsonValue> items) implements JsonValue {

    public JsonArray {
        items = java.util.Collections.unmodifiableList(new ArrayList<>(items));
    }

    public static JsonArray of() {
        return new JsonArray(List.of());
    }

    public static JsonArray of(List<JsonValue> items) {
        return new JsonArray(items);
    }
}
