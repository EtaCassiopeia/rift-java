package io.github.etacassiopeia.rift.spring;

import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.Rift;

import java.util.Map;

/**
 * The infrastructure bean registered by {@link RiftContextCustomizer}: the started {@link Rift}
 * engine, its configured imposters keyed by {@link ConfigureImposter#name()}, and the {@link Reset}
 * policy driving {@link RiftTestExecutionListener}.
 */
public final class RiftTestContext {

    private final Rift rift;
    private final Map<String, Imposter> impostersByName;
    private final Reset reset;
    private volatile boolean closed;

    RiftTestContext(Rift rift, Map<String, Imposter> impostersByName, Reset reset) {
        this.rift = rift;
        this.impostersByName = Map.copyOf(impostersByName);
        this.reset = reset;
    }

    public Rift rift() {
        return rift;
    }

    public Imposter imposter(String name) {
        Imposter imposter = impostersByName.get(name);
        if (imposter == null) {
            throw new IllegalArgumentException("no imposter configured with name '" + name + "'");
        }
        return imposter;
    }

    Reset reset() {
        return reset;
    }

    /** Resets every configured imposter: clears recorded requests, scenario state, and proxy responses. */
    void resetConfiguredImposters() {
        for (Map.Entry<String, Imposter> entry : impostersByName.entrySet()) {
            Imposter imposter = entry.getValue();
            try {
                imposter.clearRecorded();
                imposter.scenarios().reset();
                imposter.clearProxyResponses();
            } catch (RuntimeException e) {
                throw new IllegalStateException("failed to reset imposter '" + entry.getKey()
                        + "'; configured imposters: " + impostersByName.keySet(), e);
            }
        }
    }

    void close() {
        if (closed) {
            return;
        }
        closed = true;
        rift.close();
    }
}
