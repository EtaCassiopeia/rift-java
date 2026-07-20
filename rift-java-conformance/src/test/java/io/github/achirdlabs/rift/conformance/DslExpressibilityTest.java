package io.github.achirdlabs.rift.conformance;

import io.github.achirdlabs.rift.json.JsonValue;
import io.github.achirdlabs.rift.model.ImposterDefinition;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Gate 2 — DSL expressibility. Every corpus fixture runnable in this lane must be reconstructable
 * through the typed {@link io.github.achirdlabs.rift.dsl.RiftDsl} API ({@link DslFixtures}), and
 * the DSL-built imposter's serialized form must be {@link JsonValue#semanticEquals semantically
 * equal} to the fixture (modulo the {@code _verify} test annotation, which the DSL never emits).
 *
 * <p>Per issue #7, "a fixture inexpressible in the typed DSL is a red build": a runnable fixture with
 * no {@link DslFixtures} entry fails here, forcing the DSL to keep pace with the engine grammar.
 * Fixtures the lane skips (a missing capability) or that core cannot yet round-trip (a
 * {@link KnownFidelityGaps fidelity gap}) are reported as aborted with the reason, never silently
 * dropped.
 */
class DslExpressibilityTest {

    @TestFactory
    Stream<DynamicTest> dslExpressibility() {
        List<FixtureCase> fixtures = Corpus.loadOrSkip().fixtures();
        return fixtures.stream().map(fx -> DynamicTest.dynamicTest(
                "dsl-expressibility: " + fx.name(),
                () -> {
                    if (!fx.runnableInLane()) {
                        org.junit.jupiter.api.Assumptions.abort(skipReason(fx));
                    }
                    assertExpressible(fx);
                }));
    }

    private static void assertExpressible(FixtureCase fx) {
        ImposterDefinition built = DslFixtures.build(fx.number()).orElseGet(() -> {
            fail("fixture " + fx.file().getFileName() + " has no DSL expression (inexpressible = red build): "
                    + "add an entry to DslFixtures so the typed DSL keeps pace with the engine grammar.");
            throw new AssertionError("unreachable");
        });

        JsonValue expected = Normalize.stripVerify(fx.json());
        JsonValue actual = Normalize.stripVerify(JsonValue.parse(built.toJson()));
        assertTrue(
                JsonValue.semanticEquals(expected, actual),
                () -> "fixture " + fx.file().getFileName() + ": DSL-built imposter differs from the fixture at "
                        + JsonDiff.firstDifference(expected, actual).orElse("(no raw diff located)"));
    }

    private static String skipReason(FixtureCase fx) {
        if (fx.skippedInLane()) {
            return "lane-skip: fixture requires " + fx.requires() + " which the remote/spawn lane does not provide";
        }
        return "fidelity-gap: fixture cannot round-trip through the wire model yet (tracked by "
                + KnownFidelityGaps.tracker(fx.number()).orElse("(untracked)") + ")";
    }
}
