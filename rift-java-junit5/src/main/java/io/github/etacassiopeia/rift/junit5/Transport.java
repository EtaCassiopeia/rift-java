package io.github.etacassiopeia.rift.junit5;

/**
 * How {@code @RiftTest} obtains its {@link io.github.etacassiopeia.rift.Rift} engine.
 *
 * <p>This is a deliberately separate enum from the Spring integration's {@code Transport} — {@code
 * rift-java-junit5} must not depend on {@code rift-java-spring}.
 */
public enum Transport {

    /**
     * Prefer an embedded (in-process FFM) engine when available; otherwise spawn and manage a
     * {@code rift} binary for the test class lifetime.
     */
    AUTO,

    /** Connect to an already-running admin API at {@code @RiftTest.adminUri()}. */
    CONNECT,

    /** Spawn and manage a {@code rift} binary for the test class lifetime. */
    SPAWN,

    /** Run the engine in-process via {@code rift-java-embedded} (requires JDK 22+). */
    EMBEDDED
}
