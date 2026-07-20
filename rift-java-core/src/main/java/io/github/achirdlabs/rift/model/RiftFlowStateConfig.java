package io.github.achirdlabs.rift.model;

import io.github.achirdlabs.rift.json.JsonNumber;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonString;

import java.util.Optional;

/**
 * Flow state configuration: enables stateful scripting keyed by a correlation {@code flow_id}
 * (by default the imposter's port; {@code flowIdSource} can select a header instead).
 */
public record RiftFlowStateConfig(String backend, long ttlSeconds, Optional<RiftRedisConfig> redis, Optional<String> flowIdSource) {

    public static final String DEFAULT_BACKEND = "inmemory";
    public static final long DEFAULT_TTL_SECONDS = 300;

    public RiftFlowStateConfig {
        java.util.Objects.requireNonNull(backend, "backend");
        java.util.Objects.requireNonNull(redis, "redis");
        java.util.Objects.requireNonNull(flowIdSource, "flowIdSource");
    }

    static RiftFlowStateConfig read(JsonObject obj) {
        return new RiftFlowStateConfig(
                JsonSupport.optString(obj, "backend").orElse(DEFAULT_BACKEND),
                JsonSupport.optLong(obj, "ttlSeconds", DEFAULT_TTL_SECONDS),
                Optional.ofNullable(obj.get("redis")).map(v -> RiftRedisConfig.read(JsonSupport.requireObject(v, "redis"))),
                JsonSupport.optString(obj, "flowIdSource"));
    }

    JsonObject toJsonValue() {
        JsonObject.Builder builder = JsonObject.builder();
        builder.put("backend", new JsonString(backend));
        builder.put("ttlSeconds", JsonNumber.of(ttlSeconds));
        redis.ifPresent(v -> builder.put("redis", v.toJsonValue()));
        flowIdSource.ifPresent(v -> builder.put("flowIdSource", new JsonString(v)));
        return builder.build();
    }
}
