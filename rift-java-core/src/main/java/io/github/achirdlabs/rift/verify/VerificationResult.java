package io.github.achirdlabs.rift.verify;

import io.github.achirdlabs.rift.RecordedRequest;
import io.github.achirdlabs.rift.error.CommunicationError;
import io.github.achirdlabs.rift.json.JsonArray;
import io.github.achirdlabs.rift.json.JsonNumber;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The outcome of a verification, as a value rather than a pass/throw. Counts come from the engine's
 * evaluator — the same one the request hot path uses — so they never drift from a stub's matching.
 *
 * <p>{@code requests} and {@code closest} are populated only when the corresponding
 * {@link VerifyDetail} was requested; otherwise they come back empty, not absent-meaning-zero
 * ({@link #matched()} always carries the count).
 */
public record VerificationResult(
        int matched,
        int total,
        boolean satisfied,
        List<RecordedRequest> requests,
        Optional<ClosestMiss> closest) {

    public VerificationResult {
        requests = List.copyOf(requests);
        Objects.requireNonNull(closest, "closest");
    }

    /**
     * Maps a {@code POST /imposters/{port}/verify} response envelope, deciding {@code satisfied}
     * against {@code times} client-side (the engine counts; it has no expectation of its own).
     *
     * @throws CommunicationError if the envelope is not the shape the engine is contracted to
     *     return — the same class {@code EngineInfo}/{@code ApplyResult} raise for an unparseable
     *     admin-API response, so {@code catch (RiftException)} covers it
     */
    public static VerificationResult read(JsonValue value, VerificationTimes times) {
        JsonObject obj = requireObject(value, "verification result");
        int matched = requireInt(obj, "matched");
        return new VerificationResult(
                matched,
                requireInt(obj, "total"),
                times.matches(matched),
                readArray(obj, "requests", RecordedRequest::read),
                obj.has("closest") ? Optional.of(ClosestMiss.read(obj.get("closest"))) : Optional.empty());
    }

    static JsonObject requireObject(JsonValue value, String context) {
        if (value instanceof JsonObject obj) {
            return obj;
        }
        throw new CommunicationError("expected a JSON object for " + context + ", got: " + value.toJson());
    }

    static JsonValue requireField(JsonObject obj, String key, String context) {
        if (!obj.has(key)) {
            throw new CommunicationError(context + " is missing '" + key + "': " + obj.toJson());
        }
        return obj.get(key);
    }

    private static int requireInt(JsonObject obj, String key) {
        if (!(requireField(obj, key, "verification result") instanceof JsonNumber number)) {
            throw new CommunicationError("verification result '" + key + "' is not a number: " + obj.toJson());
        }
        try {
            return number.asInt();
        } catch (NumberFormatException e) {
            // JsonNumber holds the raw literal, so a fractional or out-of-range count only fails here.
            throw new CommunicationError("verification result '" + key + "' is not an int: " + number.toJson(), e);
        }
    }

    /** Reads an optional array field; an absent key is empty, a present non-array is a wire violation. */
    static <T> List<T> readArray(JsonObject obj, String key, Function<JsonValue, T> readElement) {
        if (!obj.has(key)) {
            return List.of();
        }
        if (!(obj.get(key) instanceof JsonArray array)) {
            throw new CommunicationError("expected a JSON array for '" + key + "', got: " + obj.get(key).toJson());
        }
        List<T> out = new ArrayList<>();
        for (JsonValue item : array.items()) {
            out.add(readElement.apply(item));
        }
        return List.copyOf(out);
    }
}
