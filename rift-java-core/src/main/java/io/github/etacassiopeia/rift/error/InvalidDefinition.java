package io.github.etacassiopeia.rift.error;

/** The engine rejected an imposter/stub/config definition (HTTP 400). */
public final class InvalidDefinition extends RiftException {

    public InvalidDefinition(String message) {
        super(message);
    }

    public InvalidDefinition(String message, Throwable cause) {
        super(message, cause);
    }
}
