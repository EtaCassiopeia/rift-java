package io.github.achirdlabs.rift.dsl;

import io.github.achirdlabs.rift.json.JsonValue;
import io.github.achirdlabs.rift.model.PredicateOperation;

import java.util.Map;

/**
 * A matching rule not yet bound to a request field.
 *
 * <p>Produced by {@link RiftDsl#equals(String)} and its siblings ({@code deepEquals}, {@code
 * contains}, {@code startsWith}, {@code endsWith}, {@code matches}, {@code exists}, {@code
 * notExists}), a {@code Matcher} carries just the operation kind and its raw value. It becomes a
 * full {@link io.github.achirdlabs.rift.model.Predicate} only once bound to a field via one of
 * {@link RiftDsl#method(Matcher)}, {@link RiftDsl#path(Matcher)}, {@link RiftDsl#header(String,
 * Matcher)}, {@link RiftDsl#query(String, Matcher)}, or {@link RiftDsl#body(Matcher)} — the same
 * matcher rule maps onto a different wire shape depending on which field it is attached to (a
 * bare string field for {@code method}/{@code path}, a one-level-nested object for {@code
 * headers}/{@code query}, and the matcher's raw value verbatim for {@code body}).
 */
public final class Matcher {

    /** The predicate operation this matcher will build, once bound to a field. */
    enum Kind { EQUALS, DEEP_EQUALS, CONTAINS, STARTS_WITH, ENDS_WITH, MATCHES, EXISTS }

    private final Kind kind;
    private final JsonValue value;

    private Matcher(Kind kind, JsonValue value) {
        this.kind = kind;
        this.value = value;
    }

    static Matcher create(Kind kind, JsonValue value) {
        return new Matcher(kind, value);
    }

    /** This matcher's raw value, in the shape it takes once bound to a field. */
    JsonValue value() {
        return value;
    }

    /** Builds the {@link PredicateOperation} this matcher represents, over the given flattened fields. */
    PredicateOperation toOperation(Map<String, JsonValue> fields) {
        switch (kind) {
            case EQUALS:
                return new PredicateOperation.Equals(fields);
            case DEEP_EQUALS:
                return new PredicateOperation.DeepEquals(fields);
            case CONTAINS:
                return new PredicateOperation.Contains(fields);
            case STARTS_WITH:
                return new PredicateOperation.StartsWith(fields);
            case ENDS_WITH:
                return new PredicateOperation.EndsWith(fields);
            case MATCHES:
                return new PredicateOperation.Matches(fields);
            case EXISTS:
                return new PredicateOperation.Exists(fields);
            default:
                throw new IllegalStateException("unreachable: " + kind);
        }
    }
}
