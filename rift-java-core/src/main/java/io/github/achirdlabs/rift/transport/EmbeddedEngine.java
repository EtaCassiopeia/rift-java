package io.github.achirdlabs.rift.transport;

/**
 * A started in-process rift engine, as returned by {@link EmbeddedEngineProvider#start}: the
 * transport bound to it, and the action to run to tear it down (e.g. deleting an extracted
 * temporary native library) once {@code RiftTransport#close()} has already released the engine
 * handle.
 */
public record EmbeddedEngine(RiftTransport transport, Runnable onClose) {
}
