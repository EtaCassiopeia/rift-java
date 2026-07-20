package io.github.achirdlabs.rift.model;

import io.github.achirdlabs.rift.json.JsonNumber;
import io.github.achirdlabs.rift.json.JsonObject;

/** Connection pool sizing for the Rift proxy extension's upstream connections. */
public record RiftConnectionPoolConfig(int maxIdlePerHost, long idleTimeoutSecs) {

    public static final int DEFAULT_MAX_IDLE_PER_HOST = 100;
    public static final long DEFAULT_IDLE_TIMEOUT_SECS = 90;

    static RiftConnectionPoolConfig read(JsonObject obj) {
        return new RiftConnectionPoolConfig(
                JsonSupport.optInt(obj, "maxIdlePerHost", DEFAULT_MAX_IDLE_PER_HOST),
                JsonSupport.optLong(obj, "idleTimeoutSecs", DEFAULT_IDLE_TIMEOUT_SECS));
    }

    JsonObject toJsonValue() {
        return JsonObject.builder()
                .put("maxIdlePerHost", JsonNumber.of(maxIdlePerHost))
                .put("idleTimeoutSecs", JsonNumber.of(idleTimeoutSecs))
                .build();
    }
}
