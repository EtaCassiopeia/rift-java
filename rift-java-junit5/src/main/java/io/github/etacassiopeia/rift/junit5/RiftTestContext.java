package io.github.etacassiopeia.rift.junit5;

import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.Intercept;
import io.github.etacassiopeia.rift.Recording;
import io.github.etacassiopeia.rift.Rift;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /** Set only when {@code @RiftGolden} is CAPTURE-ing; {@code afterAll} persists it before closing. */
    private Recording goldenRecording;
    private Path goldenFile;

    /** Set only when {@code @RiftIntercept} is present; {@link #close()} closes it before the engine. */
    private Intercept intercept;

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

    void setIntercept(Intercept intercept) {
        this.intercept = intercept;
    }

    /** The live intercept handle, or {@code null} when the class has no {@code @RiftIntercept}. */
    Intercept intercept() {
        return intercept;
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

    /** Records a golden CAPTURE in progress; {@link #close()} persists it to {@code file} before closing the engine. */
    void setGoldenCapture(Recording recording, Path file) {
        this.goldenRecording = recording;
        this.goldenFile = file;
    }

    void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (goldenRecording != null) {
                persistGolden();
            }
        } finally {
            try {
                if (intercept != null) {
                    intercept.close();
                }
            } finally {
                rift.close();
            }
        }
    }

    /** Persist uses the transport, so it must run while the engine is still open — before {@link Rift#close()}. */
    private void persistGolden() {
        try {
            Path parent = goldenFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create parent directories for golden file " + goldenFile, e);
        }
        goldenRecording.persist(goldenFile);
    }
}
