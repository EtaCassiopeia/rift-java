package io.github.achirdlabs.rift.error;

/**
 * The rift engine could not be reached: connection refused/timed out, the native/spawned engine
 * process is missing, or the version preflight (see {@code ConnectOptions.VersionCheck.FAIL})
 * determined the running engine is too old.
 */
public final class EngineUnavailable extends RiftException {

    public EngineUnavailable(String message) {
        super(message);
    }

    public EngineUnavailable(String message, Throwable cause) {
        super(message, cause);
    }
}
