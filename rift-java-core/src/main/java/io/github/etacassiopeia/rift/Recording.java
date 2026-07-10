package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.model.Stub;

import java.nio.file.Path;
import java.util.List;

/**
 * A proxy recording in progress, started by {@link Imposter#startRecording}. {@link #stop()}
 * swaps the live proxy stub for the stubs it has recorded so far, so subsequent requests replay
 * from those stubs instead of proxying live; {@link #snapshot()} inspects the same recorded stubs
 * without stopping the recording.
 */
public interface Recording extends AutoCloseable {

    /**
     * Stops the recording: swaps the live proxy stub for the stubs recorded so far and returns
     * them. Idempotent — calling this more than once does not re-swap or throw.
     */
    List<Stub> stop();

    /** The stubs recorded so far, without stopping the recording — the proxy keeps recording. */
    List<Stub> snapshot();

    /** Stops the recording (as {@link #stop()}) and writes the replayable imposter definition to {@code path}. */
    void persist(Path path);

    /** Stops the recording, discarding the recorded stubs. Equivalent to {@link #stop()}. */
    @Override
    default void close() {
        stop();
    }
}
