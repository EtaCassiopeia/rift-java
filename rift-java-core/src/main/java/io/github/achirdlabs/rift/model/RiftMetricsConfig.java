package io.github.achirdlabs.rift.model;

import io.github.achirdlabs.rift.json.JsonBool;
import io.github.achirdlabs.rift.json.JsonNumber;
import io.github.achirdlabs.rift.json.JsonObject;

/** Metrics endpoint configuration for Rift extensions. */
public record RiftMetricsConfig(boolean enabled, int port) {

    public static final int DEFAULT_PORT = 9090;

    static RiftMetricsConfig read(JsonObject obj) {
        return new RiftMetricsConfig(JsonSupport.optBool(obj, "enabled", false), JsonSupport.optInt(obj, "port", DEFAULT_PORT));
    }

    JsonObject toJsonValue() {
        return JsonObject.builder().put("enabled", JsonBool.of(enabled)).put("port", JsonNumber.of(port)).build();
    }
}
