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
            // Remaining gaps are string-typed statusCode ("200"), dropped `_mode`, the `behaviors`
            // array form, and a null `proxy` alongside `is` — none of which are the latency/proxy
            // default-field scope of #56; tracked separately by #60.
            20, "#60");

    private KnownFidelityGaps() {}

    static boolean contains(int fixtureNumber) {
        return GAPS.containsKey(fixtureNumber);
    }

    /** The tracking issue for a known-gapped fixture, if any. */
    static Optional<String> tracker(int fixtureNumber) {
        return Optional.ofNullable(GAPS.get(fixtureNumber));
    }
}
