package io.github.achirdlabs.rift.model;

import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonString;

/** Path rewrite configuration for proxy responses: replaces the {@code from} substring with {@code to}. */
public record PathRewrite(String from, String to) {

    public PathRewrite {
        java.util.Objects.requireNonNull(from, "from");
        java.util.Objects.requireNonNull(to, "to");
    }

    static PathRewrite read(JsonObject obj) {
        return new PathRewrite(JsonSupport.requireString(obj, "from"), JsonSupport.requireString(obj, "to"));
    }

    JsonObject toJsonValue() {
        return JsonObject.builder().put("from", new JsonString(from)).put("to", new JsonString(to)).build();
    }
}
