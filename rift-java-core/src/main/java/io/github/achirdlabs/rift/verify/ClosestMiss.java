package io.github.achirdlabs.rift.verify;

import io.github.achirdlabs.rift.RecordedRequest;
import io.github.achirdlabs.rift.json.JsonValue;

import java.util.List;
import java.util.Objects;

/**
 * The recorded request that satisfied the most predicate clauses without matching, and the clauses
 * it failed. Scored by the engine's own evaluator, so it agrees with the verdict it explains.
 */
public record ClosestMiss(RecordedRequest request, List<FailedPredicate> failedPredicates) {

    public ClosestMiss {
        Objects.requireNonNull(request, "request");
        failedPredicates = List.copyOf(failedPredicates);
    }

    static ClosestMiss read(JsonValue value) {
        var obj = VerificationResult.requireObject(value, "closest");
        return new ClosestMiss(
                RecordedRequest.read(VerificationResult.requireField(obj, "request", "closest")),
                VerificationResult.readArray(obj, "failedPredicates", FailedPredicate::read));
    }
}
