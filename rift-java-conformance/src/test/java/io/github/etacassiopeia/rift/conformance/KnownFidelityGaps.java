package io.github.etacassiopeia.rift.conformance;

import java.util.Map;
import java.util.Optional;

/**
 * The corpus fixtures that {@code rift-java-core} cannot yet round-trip through {@code
 * ImposterDefinition.fromJson → toJson} with fidelity, each mapped to the core issue tracking the
 * gap. These are engine-canonical wire forms the SDK model does not yet parse or re-serialize
 * exactly — the "fidelity issue" work that issue #7's parse gate exists to surface.
 *
 * <p>This is deliberately <em>not</em> a way to silence the gate. {@link ParseFidelityTest} asserts
 * that every listed fixture <em>still fails</em> the round-trip: the day core is fixed, the fixture
 * starts round-tripping, the xfail assertion flips red, and whoever fixed core is forced to delete
 * the entry here and let the fixture join the enforced set. A gap can never rot into a silent pass.
 *
 * <p>A fidelity-gapped fixture is also excluded from the DSL-expressibility gate and engine replay:
 * a fixture the model cannot even parse faithfully is not yet a meaningful DSL-parity target.
 */
final class KnownFidelityGaps {

    /** Fixture number → the core issue tracking why it cannot round-trip yet. */
    private static final Map<Integer, String> GAPS = Map.of(
            3, "#55",   // `copy` object-form not parsed (WireFormatException)
            14, "#55",  // `copy` object-form not parsed
            16, "#55",  // `copy` object-form not parsed
            20, "#55",  // `wait` string-form not parsed
            4, "#56",   // latency fault emits default minMs/maxMs:0 alongside fixed `ms`
            7, "#56");  // proxy response emits default addWaitBehavior

    private KnownFidelityGaps() {}

    static boolean contains(int fixtureNumber) {
        return GAPS.containsKey(fixtureNumber);
    }

    /** The tracking issue for a known-gapped fixture, if any. */
    static Optional<String> tracker(int fixtureNumber) {
        return Optional.ofNullable(GAPS.get(fixtureNumber));
    }
}
