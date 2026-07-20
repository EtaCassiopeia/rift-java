package io.github.achirdlabs.rift.model;

import io.github.achirdlabs.rift.json.JsonNumber;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonString;

/** Global script engine defaults for an imposter's {@code _rift} block. */
public record RiftScriptEngineConfig(String defaultEngine, long timeoutMs) {

    public static final String DEFAULT_ENGINE = "rhai";
    public static final long DEFAULT_TIMEOUT_MS = 30_000L;

    public RiftScriptEngineConfig {
        java.util.Objects.requireNonNull(defaultEngine, "defaultEngine");
    }

    static RiftScriptEngineConfig read(JsonObject obj) {
        return new RiftScriptEngineConfig(
                JsonSupport.optString(obj, "defaultEngine").orElse(DEFAULT_ENGINE),
                JsonSupport.optLong(obj, "timeoutMs", DEFAULT_TIMEOUT_MS));
    }

    JsonObject toJsonValue() {
        return JsonObject.builder()
                .put("defaultEngine", new JsonString(defaultEngine))
                .put("timeoutMs", JsonNumber.of(timeoutMs))
                .build();
    }
}
