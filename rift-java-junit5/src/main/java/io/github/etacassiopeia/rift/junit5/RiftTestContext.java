package io.github.etacassiopeia.rift.junit5;

import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.Rift;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The per-class infrastructure {@link RiftTestExtension} builds: the started {@link Rift} engine,
 * its configured imposters keyed by their declared {@code ImposterDefinition} name, and the {@link Reset} policy driving
 * {@link RiftTestExtension#beforeEach}.
 */
final class RiftTestContext {

    private final Rift rift;
    private final Map<String, Imposter> impostersByName;
    private final Reset reset;
    private final boolean dumpRecordedOnFailure;
    private volatile boolean closed;

    RiftTestContext(Rift rift, Map<String, Imposter> impostersByName, Reset reset, boolean dumpRecordedOnFailure) {
        this.rift = rift;
        // Preserve declaration order (unlike Map.copyOf) so forEachImposter — and thus the failure
        // dump — is deterministic across runs.
        this.impostersByName = Collections.unmodifiableMap(new LinkedHashMap<>(impostersByName));
        this.reset = reset;
        this.dumpRecordedOnFailure = dumpRecordedOnFailure;
    }

    Rift rift() {
        return rift;
    }

    boolean dumpRecordedOnFailure() {
        return dumpRecordedOnFailure;
    }

    /** Applies {@code action} to each configured imposter (name → imposter), in declaration order. */
    void forEachImposter(BiConsumer<String, Imposter> action) {
        impostersByName.forEach(action);
    }

    Imposter imposter(String name) {
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
