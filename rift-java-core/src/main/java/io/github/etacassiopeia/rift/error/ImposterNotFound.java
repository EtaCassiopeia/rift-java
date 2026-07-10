package io.github.etacassiopeia.rift.error;

/** An operation referenced {@link #port()}, but no imposter is bound to it. */
public final class ImposterNotFound extends RiftException {

    private final int port;

    public ImposterNotFound(int port, String message) {
        super(message);
        this.port = port;
    }

    public int port() {
        return port;
    }
}
