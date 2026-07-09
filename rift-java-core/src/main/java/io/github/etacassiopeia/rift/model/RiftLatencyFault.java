package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonNumber;
import io.github.etacassiopeia.rift.json.JsonObject;

import java.util.Optional;

/** Latency fault injection: either a fixed {@code ms} delay, or a {@code minMs}/{@code maxMs} range. */
public record RiftLatencyFault(double probability, long minMs, long maxMs, Optional<Long> ms) {

    public static final double DEFAULT_PROBABILITY = 1.0;

    public RiftLatencyFault {
        java.util.Objects.requireNonNull(ms, "ms");
    }

    static RiftLatencyFault read(JsonObject obj) {
        return new RiftLatencyFault(
                JsonSupport.optDouble(obj, "probability", DEFAULT_PROBABILITY),
                JsonSupport.optLong(obj, "minMs", 0),
                JsonSupport.optLong(obj, "maxMs", 0),
                JsonSupport.optLongBox(obj, "ms"));
    }

    JsonObject toJsonValue() {
        JsonObject.Builder builder = JsonObject.builder();
        builder.put("probability", JsonNumber.of(probability));
        builder.put("minMs", JsonNumber.of(minMs));
        builder.put("maxMs", JsonNumber.of(maxMs));
        ms.ifPresent(v -> builder.put("ms", JsonNumber.of(v)));
        return builder.build();
    }
}
