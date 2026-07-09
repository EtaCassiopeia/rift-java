package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;

import java.util.Optional;

/** The extraction method for a {@code copy} behavior entry: regex, xpath, or jsonpath. */
public record Using(String method, Optional<String> selector, Optional<JsonValue> options) {

    public Using {
        java.util.Objects.requireNonNull(method, "method");
        java.util.Objects.requireNonNull(selector, "selector");
        java.util.Objects.requireNonNull(options, "options");
    }

    static Using read(JsonObject obj) {
        return new Using(
                JsonSupport.requireString(obj, "method"),
                JsonSupport.optString(obj, "selector"),
                Optional.ofNullable(obj.get("options")));
    }

    JsonObject toJsonValue() {
        JsonObject.Builder builder = JsonObject.builder().put("method", new JsonString(method));
        selector.ifPresent(v -> builder.put("selector", new JsonString(v)));
        options.ifPresent(v -> builder.put("options", v));
        return builder.build();
    }
}
