package io.github.achirdlabs.rift.model;

import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonValue;

/**
 * A single predicate: matcher {@link PredicateParameters} plus the {@link PredicateOperation} to
 * apply. Both are flattened into one JSON object on the wire, e.g.
 * {@code {"equals": {"path": "/hello"}, "caseSensitive": false}}.
 */
public record Predicate(PredicateParameters parameters, PredicateOperation operation) {

    public Predicate {
        java.util.Objects.requireNonNull(parameters, "parameters");
        java.util.Objects.requireNonNull(operation, "operation");
    }

    public Predicate(PredicateOperation operation) {
        this(PredicateParameters.EMPTY, operation);
    }

    /** Parses a single predicate JSON object. Throws a typed codec error on malformed input. */
    public static Predicate fromJson(String json) {
        return read(JsonSupport.requireObject(JsonValue.parse(json), "predicate"));
    }

    public String toJson() {
        return toJsonValue().toJson();
    }

    static Predicate read(JsonObject obj) {
        return new Predicate(PredicateParameters.read(obj), PredicateOperation.read(obj));
    }

    JsonValue toJsonValue() {
        JsonObject.Builder builder = JsonObject.builder();
        builder.put(operation.tag(), operation.value());
        parameters.writeInto(builder);
        return builder.build();
    }
}
