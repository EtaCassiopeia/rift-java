package io.github.achirdlabs.rift.model;

import io.github.achirdlabs.rift.json.JsonNumber;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonString;

/** Redis backend configuration for flow state. */
public record RiftRedisConfig(String url, int poolSize, String keyPrefix) {

    public static final int DEFAULT_POOL_SIZE = 10;
    public static final String DEFAULT_KEY_PREFIX = "rift:";

    public RiftRedisConfig {
        java.util.Objects.requireNonNull(url, "url");
        java.util.Objects.requireNonNull(keyPrefix, "keyPrefix");
    }

    static RiftRedisConfig read(JsonObject obj) {
        return new RiftRedisConfig(
                JsonSupport.requireString(obj, "url"),
                JsonSupport.optInt(obj, "poolSize", DEFAULT_POOL_SIZE),
                JsonSupport.optString(obj, "keyPrefix").orElse(DEFAULT_KEY_PREFIX));
    }

    JsonObject toJsonValue() {
        return JsonObject.builder()
                .put("url", new JsonString(url))
                .put("poolSize", JsonNumber.of(poolSize))
                .put("keyPrefix", new JsonString(keyPrefix))
                .build();
    }
}
