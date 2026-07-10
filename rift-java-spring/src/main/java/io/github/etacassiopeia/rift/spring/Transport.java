package io.github.etacassiopeia.rift.spring;

/**
 * How the Spring test integration obtains its {@link io.github.etacassiopeia.rift.Rift} engine.
 *
 * <p>This is a deliberately separate enum from the JUnit 5 module's {@code Transport} — {@code
 * rift-java-spring} must not depend on {@code rift-java-junit5}.
 */
public enum Transport {

    /**
     * Prefer a managed {@code spawn}ed engine; when {@code adminUri} is set, connect to it instead.
     *
     * <p>An embedded (in-process FFM) engine will become the first preference here once
     * {@code Rift.embedded()} ships (issue #10); until then AUTO resolves to connect-or-spawn.
     */
    AUTO,

    /** Connect to an already-running admin API at {@code @EnableRift.adminUri()}. */
    CONNECT,

    /** Spawn and manage a {@code rift} binary for the test context lifetime. */
    SPAWN
}
