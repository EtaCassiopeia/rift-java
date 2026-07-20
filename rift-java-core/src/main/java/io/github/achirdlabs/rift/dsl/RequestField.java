package io.github.achirdlabs.rift.dsl;

/** A part of a proxied request a predicate generator can match on. */
public enum RequestField {

    METHOD("method"),
    PATH("path"),
    QUERY("query"),
    HEADERS("headers"),
    BODY("body");

    private final String wire;

    RequestField(String wire) {
        this.wire = wire;
    }

    /** The wire key this field serializes as under {@code matches}. */
    String wire() {
        return wire;
    }
}
