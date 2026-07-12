package io.github.etacassiopeia.rift.verify;

import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.Predicate;
import io.github.etacassiopeia.rift.model.WireFormatException;

import java.util.ArrayList;
import java.util.List;

/**
 * Something that matches requests against a list of predicates — implemented by {@code StubSpec} so
 * a stub under construction can be inspected/verified before (or without) being built into a {@code
 * Stub}. The {@code of}/{@code ofJson} factories are the verification-path escape hatch (mirroring
 * the raw-JSON creation hatches): they build a match straight from predicates or from the wire
 * {@code predicates} array, so a bridge that authors stubs as raw JSON can verify the same way.
 */
public interface RequestMatch {

    /** The predicates a request must satisfy to match. */
    List<Predicate> predicates();

    /** A match over the given predicates (defensively copied). */
    static RequestMatch of(List<Predicate> predicates) {
        List<Predicate> copy = List.copyOf(predicates);
        return () -> copy;
    }

    /** A match over the given predicates. */
    static RequestMatch of(Predicate... predicates) {
        return of(List.of(predicates));
    }

    /**
     * A match parsed from a wire {@code predicates} array — the same shape a stub's {@code predicates}
     * field carries.
     *
     * @throws WireFormatException if {@code predicateArray} is not a JSON array, or an element is not a
     *     valid predicate (the message names the offending index)
     */
    static RequestMatch ofJson(JsonValue predicateArray) {
        if (!(predicateArray instanceof JsonArray array)) {
            throw new WireFormatException(
                    "expected a JSON array of predicates, got: " + predicateArray.toJson());
        }
        List<Predicate> predicates = new ArrayList<>();
        for (int i = 0; i < array.items().size(); i++) {
            try {
                predicates.add(Predicate.fromJson(array.items().get(i).toJson()));
            } catch (RuntimeException e) {
                throw new WireFormatException("invalid predicate at index " + i + ": " + e.getMessage());
            }
        }
        return of(predicates);
    }

    /** A match parsed from a wire {@code predicates} array in JSON text. */
    static RequestMatch ofJson(String predicateArrayJson) {
        return ofJson(JsonValue.parse(predicateArrayJson));
    }
}
