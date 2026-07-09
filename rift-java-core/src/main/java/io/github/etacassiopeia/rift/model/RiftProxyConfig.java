package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonObject;

import java.util.Optional;

/** Upstream + connection-pool configuration for the Rift proxy extension. */
public record RiftProxyConfig(Optional<RiftUpstreamConfig> upstream, Optional<RiftConnectionPoolConfig> connectionPool) {

    public RiftProxyConfig {
        java.util.Objects.requireNonNull(upstream, "upstream");
        java.util.Objects.requireNonNull(connectionPool, "connectionPool");
    }

    static RiftProxyConfig read(JsonObject obj) {
        return new RiftProxyConfig(
                Optional.ofNullable(obj.get("upstream")).map(v -> RiftUpstreamConfig.read(JsonSupport.requireObject(v, "upstream"))),
                Optional.ofNullable(obj.get("connectionPool")).map(v -> RiftConnectionPoolConfig.read(JsonSupport.requireObject(v, "connectionPool"))));
    }

    JsonObject toJsonValue() {
        JsonObject.Builder builder = JsonObject.builder();
        upstream.ifPresent(v -> builder.put("upstream", v.toJsonValue()));
        connectionPool.ifPresent(v -> builder.put("connectionPool", v.toJsonValue()));
        return builder.build();
    }
}
