package io.github.achirdlabs.rift.model;

import io.github.achirdlabs.rift.json.JsonBool;
import io.github.achirdlabs.rift.json.JsonObject;

import java.util.Optional;

/**
 * Response-level {@code _rift} extension block: fault injection, script-based generation, and
 * opt-in response templating.
 */
public record RiftResponseExtension(Optional<RiftFaultConfig> fault, Optional<RiftScriptConfig> script, boolean templated) {

    public static final RiftResponseExtension EMPTY = new RiftResponseExtension(Optional.empty(), Optional.empty(), false);

    public RiftResponseExtension {
        java.util.Objects.requireNonNull(fault, "fault");
        java.util.Objects.requireNonNull(script, "script");
    }

    static RiftResponseExtension read(JsonObject obj) {
        return new RiftResponseExtension(
                Optional.ofNullable(obj.get("fault")).map(v -> RiftFaultConfig.read(JsonSupport.requireObject(v, "fault"))),
                Optional.ofNullable(obj.get("script")).map(v -> RiftScriptConfig.read(JsonSupport.requireObject(v, "script"))),
                JsonSupport.optBool(obj, "templated", false));
    }

    JsonObject toJsonValue() {
        JsonObject.Builder builder = JsonObject.builder();
        fault.ifPresent(v -> builder.put("fault", v.toJsonValue()));
        script.ifPresent(v -> builder.put("script", v.toJsonValue()));
        if (templated) {
            builder.put("templated", JsonBool.TRUE);
        }
        return builder.build();
    }
}
