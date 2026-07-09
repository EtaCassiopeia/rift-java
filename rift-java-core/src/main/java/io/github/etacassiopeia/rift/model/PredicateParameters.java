package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonBool;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;

import java.util.Optional;

/**
 * Matcher parameters shared across every predicate operation: case sensitivity, an {@code except}
 * regex, and an optional structured {@link PredicateSelector}. Flattened into the same JSON object
 * as {@link PredicateOperation} on the wire.
 */
public record PredicateParameters(
        Optional<Boolean> caseSensitive,
        Optional<Boolean> keyCaseSensitive,
        String except,
        Optional<PredicateSelector> selector) {

    public static final PredicateParameters EMPTY =
            new PredicateParameters(Optional.empty(), Optional.empty(), "", Optional.empty());

    public PredicateParameters {
        java.util.Objects.requireNonNull(except, "except");
    }

    static PredicateParameters read(JsonObject obj) {
        return new PredicateParameters(
                JsonSupport.optBoolBox(obj, "caseSensitive"),
                JsonSupport.optBoolBox(obj, "keyCaseSensitive"),
                JsonSupport.optString(obj, "except").orElse(""),
                PredicateSelector.read(obj));
    }

    void writeInto(JsonObject.Builder builder) {
        caseSensitive.ifPresent(v -> builder.put("caseSensitive", JsonBool.of(v)));
        keyCaseSensitive.ifPresent(v -> builder.put("keyCaseSensitive", JsonBool.of(v)));
        if (!except.isEmpty()) {
            builder.put("except", new JsonString(except));
        }
        selector.ifPresent(s -> s.writeInto(builder));
    }
}
