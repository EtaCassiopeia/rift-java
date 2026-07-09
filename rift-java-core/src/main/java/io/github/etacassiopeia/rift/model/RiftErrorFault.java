package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonNumber;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;

import java.util.Map;
import java.util.Optional;

/** Error fault injection: respond with {@code status}/{@code body}/{@code headers} instead of proxying. */
public record RiftErrorFault(double probability, int status, Optional<String> body, Map<String, String> headers) {

    public static final double DEFAULT_PROBABILITY = 1.0;
    public static final int DEFAULT_STATUS = 503;

    public RiftErrorFault {
        java.util.Objects.requireNonNull(body, "body");
        headers = JsonSupport.orderedCopy(headers);
    }

    static RiftErrorFault read(JsonObject obj) {
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        if (obj.get("headers") != null) {
            for (var e : JsonSupport.requireObject(obj.get("headers"), "headers").fields().entrySet()) {
                headers.put(e.getKey(), JsonSupport.requireString(e.getValue(), "'headers." + e.getKey() + "'"));
            }
        }
        return new RiftErrorFault(
                JsonSupport.optDouble(obj, "probability", DEFAULT_PROBABILITY),
                JsonSupport.optInt(obj, "status", DEFAULT_STATUS),
                JsonSupport.optString(obj, "body"),
                headers);
    }

    JsonObject toJsonValue() {
        JsonObject.Builder builder = JsonObject.builder();
        builder.put("probability", JsonNumber.of(probability));
        builder.put("status", JsonNumber.of(status));
        body.ifPresent(v -> builder.put("body", new JsonString(v)));
        if (!headers.isEmpty()) {
            JsonObject.Builder headersBuilder = JsonObject.builder();
            headers.forEach((k, v) -> headersBuilder.put(k, new JsonString(v)));
            builder.put("headers", headersBuilder.build());
        }
        return builder.build();
    }
}
