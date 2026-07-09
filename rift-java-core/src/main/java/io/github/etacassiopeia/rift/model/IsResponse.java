package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonNumber;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A literal ("is") response: status, headers, body, and body encoding mode.
 *
 * <p>{@code statusCode} is kept as its canonical wire string (Mountebank always serializes it as
 * a string) even though a fixture may supply it as a JSON number — {@link #read} accepts both.
 *
 * <p>{@code headers} supports Mountebank's multi-value convention: a header may appear as a bare
 * string (one value) or an array (multiple values); a single value is always written back as a
 * bare string, never a one-element array, so re-serializing always converges on the same shape.
 */
public record IsResponse(String statusCode, Map<String, List<String>> headers, Optional<JsonValue> body, ResponseMode mode) {

    public static final String DEFAULT_STATUS_CODE = "200";

    public IsResponse {
        java.util.Objects.requireNonNull(statusCode, "statusCode");
        headers = JsonSupport.orderedCopy(headers);
        java.util.Objects.requireNonNull(body, "body");
        java.util.Objects.requireNonNull(mode, "mode");
    }

    public IsResponse(String statusCode) {
        this(statusCode, Map.of(), Optional.empty(), ResponseMode.TEXT);
    }

    static IsResponse read(JsonObject obj) {
        return new IsResponse(
                readStatusCode(obj.get("statusCode")),
                readHeaders(obj.get("headers")),
                Optional.ofNullable(obj.get("body")),
                Optional.ofNullable(obj.get("_mode"))
                        .map(v -> ResponseMode.read(JsonSupport.requireString(v, "'_mode'")))
                        .orElse(ResponseMode.TEXT));
    }

    static String readStatusCode(JsonValue v) {
        if (v == null) {
            return DEFAULT_STATUS_CODE;
        }
        if (v instanceof JsonString s) {
            return s.value();
        }
        if (v instanceof JsonNumber n) {
            return n.raw();
        }
        throw new WireFormatException("'statusCode': expected a number or string, got " + JsonSupport.typeName(v));
    }

    static Map<String, List<String>> readHeaders(JsonValue v) {
        if (v == null) {
            return Map.of();
        }
        JsonObject obj = JsonSupport.requireObject(v, "headers");
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (var e : obj.fields().entrySet()) {
            out.put(e.getKey(), readHeaderValues(e.getValue(), e.getKey()));
        }
        return out;
    }

    private static List<String> readHeaderValues(JsonValue v, String key) {
        if (v instanceof JsonString s) {
            return List.of(s.value());
        }
        if (v instanceof JsonArray arr) {
            return arr.items().stream()
                    .map(el -> {
                        if (el instanceof JsonString s) {
                            return s.value();
                        }
                        throw new WireFormatException("'headers." + key + "': array elements must be strings");
                    })
                    .toList();
        }
        throw new WireFormatException("'headers." + key + "': expected a string or array of strings");
    }

    JsonObject toJsonValue() {
        return toJsonValue(false);
    }

    /**
     * Writes this response. A stub response serializes {@code statusCode} as a string (Mountebank
     * convention); an imposter's {@code defaultResponse} serializes it as a JSON number, because the
     * engine deserializes that position into a {@code u16} — hence the {@code statusCodeAsNumber}.
     */
    JsonObject toJsonValue(boolean statusCodeAsNumber) {
        JsonObject.Builder builder = JsonObject.builder();
        builder.put("statusCode", statusCodeAsNumber ? statusCodeAsNumber() : new JsonString(statusCode));
        if (!headers.isEmpty()) {
            builder.put("headers", writeHeaders(headers));
        }
        body.ifPresent(b -> builder.put("body", b));
        if (mode != ResponseMode.TEXT) {
            builder.put("_mode", new JsonString(mode.wire()));
        }
        return builder.build();
    }

    /** The status as a JSON number for the defaultResponse position; falls back to a string only if
     * the canonical status text is somehow non-integer (never true for a well-formed status). */
    private JsonValue statusCodeAsNumber() {
        try {
            return JsonNumber.of(Integer.parseInt(statusCode));
        } catch (NumberFormatException e) {
            return new JsonString(statusCode);
        }
    }

    static JsonObject writeHeaders(Map<String, List<String>> headers) {
        JsonObject.Builder builder = JsonObject.builder();
        headers.forEach((key, values) -> {
            if (values.isEmpty()) {
                return; // a header with no values would emit no header line; omit it
            }
            if (values.size() == 1) {
                builder.put(key, new JsonString(values.get(0)));
            } else {
                builder.put(key, new JsonArray(values.stream().map(v -> (JsonValue) new JsonString(v)).toList()));
            }
        });
        return builder.build();
    }
}
