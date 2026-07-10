package io.github.etacassiopeia.rift.error;

/** Any non-2xx admin API response not covered by a more specific leaf, carrying its HTTP {@link #code()}. */
public final class EngineError extends RiftException {

    private final int code;

    public EngineError(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
