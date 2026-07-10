package io.github.etacassiopeia.rift;

/**
 * Placeholder configuration for {@link Rift#embedded(EmbeddedOptions)}. The embedded transport
 * (running the engine in-process) requires the {@code rift-java-embedded} module (issue #10);
 * until then {@code Rift.embedded(...)} always throws.
 */
public final class EmbeddedOptions {

    public EmbeddedOptions() {
    }
}
