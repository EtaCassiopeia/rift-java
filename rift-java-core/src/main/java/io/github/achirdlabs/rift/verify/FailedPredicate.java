package io.github.achirdlabs.rift.verify;

import io.github.achirdlabs.rift.error.CommunicationError;
import io.github.achirdlabs.rift.json.JsonValue;
import io.github.achirdlabs.rift.model.Predicate;
import io.github.achirdlabs.rift.model.WireFormatException;

import java.util.Objects;

/**
 * One predicate clause the {@link ClosestMiss} request failed, paired with the request's actual
 * value(s) for the fields that predicate references — the raw material for a readable diff.
 *
 * <p>{@code actual} stays raw {@link JsonValue}: its shape follows whichever fields the predicate
 * names, so there is no fixed type to map it to.
 */
public record FailedPredicate(Predicate predicate, JsonValue actual) {

    public FailedPredicate {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(actual, "actual");
    }

    static FailedPredicate read(JsonValue value) {
        var obj = VerificationResult.requireObject(value, "failedPredicate");
        JsonValue predicate = VerificationResult.requireField(obj, "predicate", "failedPredicate");
        try {
            return new FailedPredicate(
                    Predicate.fromJson(predicate.toJson()),
                    VerificationResult.requireField(obj, "actual", "failedPredicate"));
        } catch (WireFormatException e) {
            // The engine echoes back a predicate we sent, so an unparseable one is the engine
            // answering off-contract — report it as such rather than as a caller wire-shape error.
            throw new CommunicationError("verification result has an unparseable failed predicate: "
                    + predicate.toJson(), e);
        }
    }
}
