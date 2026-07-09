package io.github.etacassiopeia.rift.model;

/** Response body encoding: UTF-8 text (default) or base64-encoded binary. Wire form is lowercase. */
public enum ResponseMode {
    TEXT,
    BINARY;

    static ResponseMode read(String wire) {
        return switch (wire) {
            case "text" -> TEXT;
            case "binary" -> BINARY;
            default -> throw new WireFormatException("'_mode': unknown response mode '" + wire + "'");
        };
    }

    String wire() {
        return this == TEXT ? "text" : "binary";
    }
}
