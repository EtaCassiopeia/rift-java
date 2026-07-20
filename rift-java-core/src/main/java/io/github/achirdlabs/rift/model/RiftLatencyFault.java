package io.github.achirdlabs.rift.model;

import io.github.achirdlabs.rift.json.JsonNumber;
import io.github.achirdlabs.rift.json.JsonObject;

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
        // The two forms are mutually exclusive on the wire: a fixed `ms` delay, or a `minMs`/`maxMs`
        // range. Emit only the parsed form so an input carrying just one does not gain the other's
        // fields on write (issue #56).
        if (ms.isPresent()) {
            builder.put("ms", JsonNumber.of(ms.get()));
        } else {
            builder.put("minMs", JsonNumber.of(minMs));
            builder.put("maxMs", JsonNumber.of(maxMs));
        }
        return builder.build();
    }
}
