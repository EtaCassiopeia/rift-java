package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonNumber;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;

/** Upstream target for the Rift proxy extension. */
public record RiftUpstreamConfig(String host, int port, String protocol) {

    public static final String DEFAULT_PROTOCOL = "http";

    public RiftUpstreamConfig {
        java.util.Objects.requireNonNull(host, "host");
        java.util.Objects.requireNonNull(protocol, "protocol");
    }

    static RiftUpstreamConfig read(JsonObject obj) {
        return new RiftUpstreamConfig(
                JsonSupport.requireString(obj, "host"),
                JsonSupport.requireInt(obj, "port"),
                JsonSupport.optString(obj, "protocol").orElse(DEFAULT_PROTOCOL));
    }

    JsonObject toJsonValue() {
        return JsonObject.builder()
                .put("host", new JsonString(host))
                .put("port", JsonNumber.of(port))
                .put("protocol", new JsonString(protocol))
                .build();
    }
}
