package io.github.etacassiopeia.rift.conformance;

import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.ImposterDefinition;

import java.nio.file.Path;
import java.util.Set;

/**
 * One corpus fixture, as indexed by {@code manifest.json} and loaded from
 * {@code corpus/imposters/NN-name.json}.
 *
 * @param number   the leading {@code NN} in the file name (stable fixture identity, never renumbered)
 * @param name      the human-readable manifest name (e.g. {@code "02 · Predicate Showcase"})
 * @param port      the port the fixture binds (from the manifest)
 * @param requires  capability gates from the closed set {@code injection, proxy, redis, https, shell}
 * @param hasVerify whether the fixture carries {@code _verify} request/response transcripts
 * @param file      the absolute path to the fixture JSON on disk
 * @param rawJson   the fixture's raw JSON text (the ground truth for the parse-fidelity gate)
 */
record FixtureCase(
        int number,
        String name,
        int port,
        Set<String> requires,
        boolean hasVerify,
        Path file,
        String rawJson) {

    /** The fixture JSON parsed into the generic tree — the expected side of every comparison. */
    JsonValue json() {
        return JsonValue.parse(rawJson);
    }

    /** The fixture parsed through the typed wire model (the parse gate's subject). */
    ImposterDefinition imposter() {
        return ImposterDefinition.fromJson(rawJson);
    }

    /**
     * Capabilities the remote/spawn lane cannot provide: {@code proxy} (no upstream is stood up),
     * plus {@code redis}/{@code https}/{@code shell}. {@code injection} is provided (the engine is
     * spawned with {@code allowInjection}). Per the corpus replay contract §4 a fixture is skipped
     * only when its {@code requires} names a capability the lane lacks.
     */
    private static final Set<String> UNAVAILABLE = Set.of("proxy", "redis", "https", "shell");

    /** True when this fixture must be skipped in the remote/spawn lane (a required capability is absent). */
    boolean skippedInLane() {
        return requires.stream().anyMatch(UNAVAILABLE::contains);
    }

    /**
     * True when this fixture can be exercised end-to-end in this lane: the lane provides every
     * capability it needs, and the wire model round-trips it faithfully (no {@link KnownFidelityGaps
     * known fidelity gap}). Only runnable fixtures are held to the DSL-expressibility and replay gates.
     */
    boolean runnableInLane() {
        return !skippedInLane() && !KnownFidelityGaps.contains(number);
    }
}
