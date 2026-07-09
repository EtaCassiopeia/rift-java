package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;

import java.util.Optional;

/** Fault injection configuration for a response: latency, an error response, or a raw TCP fault. */
public record RiftFaultConfig(Optional<RiftLatencyFault> latency, Optional<RiftErrorFault> error, Optional<String> tcp) {

    public RiftFaultConfig {
        java.util.Objects.requireNonNull(latency, "latency");
        java.util.Objects.requireNonNull(error, "error");
        java.util.Objects.requireNonNull(tcp, "tcp");
    }

    static RiftFaultConfig read(JsonObject obj) {
        return new RiftFaultConfig(
                Optional.ofNullable(obj.get("latency")).map(v -> RiftLatencyFault.read(JsonSupport.requireObject(v, "latency"))),
                Optional.ofNullable(obj.get("error")).map(v -> RiftErrorFault.read(JsonSupport.requireObject(v, "error"))),
                JsonSupport.optString(obj, "tcp"));
    }

    JsonObject toJsonValue() {
        JsonObject.Builder builder = JsonObject.builder();
        latency.ifPresent(v -> builder.put("latency", v.toJsonValue()));
        error.ifPresent(v -> builder.put("error", v.toJsonValue()));
        tcp.ifPresent(v -> builder.put("tcp", new JsonString(v)));
        return builder.build();
    }
}
