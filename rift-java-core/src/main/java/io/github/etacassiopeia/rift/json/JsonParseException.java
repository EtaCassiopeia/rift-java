package io.github.etacassiopeia.rift.json;

/**
 * Thrown by {@link JsonReader} when input text is not well-formed JSON. Carries the 1-based
 * line/column of the failure so callers get a locatable error instead of a generic parse failure.
 */
public final class JsonParseException extends RuntimeException {

    private final int line;
    private final int column;

    public JsonParseException(String message, int line, int column) {
        super(message + " (line " + line + ", column " + column + ")");
        this.line = line;
        this.column = column;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }
}
