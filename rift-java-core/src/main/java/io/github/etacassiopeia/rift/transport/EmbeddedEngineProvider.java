package io.github.etacassiopeia.rift.transport;

import io.github.etacassiopeia.rift.EmbeddedOptions;

/**
 * SPI implemented by {@code rift-java-embedded} (discovered via {@link java.util.ServiceLoader})
 * so that {@code rift-java-core} can offer {@code Rift.embedded(...)} without depending on the
 * Panama FFM module, which requires JDK 22+.
 */
public interface EmbeddedEngineProvider {

    /** Whether this provider can currently resolve a native library (env/property/classpath). */
    boolean isAvailable();

    /** Resolves the native library per {@code options} and starts the in-process engine. */
    EmbeddedEngine start(EmbeddedOptions options);
}
