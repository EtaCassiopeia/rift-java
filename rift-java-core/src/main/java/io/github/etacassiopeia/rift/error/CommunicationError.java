package io.github.etacassiopeia.rift.error;

/** The engine answered with a successful (2xx) status, but the response body could not be parsed. */
public final class CommunicationError extends RiftException {

    public CommunicationError(String message) {
        super(message);
    }

    public CommunicationError(String message, Throwable cause) {
        super(message, cause);
    }
}
