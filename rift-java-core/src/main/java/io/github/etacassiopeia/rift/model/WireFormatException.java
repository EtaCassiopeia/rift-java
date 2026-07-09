package io.github.etacassiopeia.rift.model;

/**
 * Thrown when JSON text is syntactically well-formed but does not match the shape the wire model
 * requires — e.g. a predicate object naming zero or more than one operation key, or a field whose
 * value has the wrong JSON type. Distinct from {@link io.github.etacassiopeia.rift.json.JsonParseException},
 * which reports a syntax error in the JSON text itself.
 */
public final class WireFormatException extends RuntimeException {

    public WireFormatException(String message) {
        super(message);
    }
}
