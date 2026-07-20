package io.github.achirdlabs.rift.conformance;

import io.github.achirdlabs.rift.json.JsonValue;
import io.github.achirdlabs.rift.model.ImposterDefinition;
import io.github.achirdlabs.rift.model.WireFormatException;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate 1 — parse fidelity. For every corpus fixture, {@link ImposterDefinition#fromJson} must parse
 * it and {@link ImposterDefinition#toJson} must serialize back to a tree {@link
 * JsonValue#semanticEquals semantically equal} to the fixture. A dropped/renamed/retyped key — the
 * loss the {@code extra}-map machinery exists to prevent — is a red build.
 */
class ParseFidelityTest {

    @TestFactory
    Stream<DynamicTest> parseFidelity() {
        List<FixtureCase> fixtures = Corpus.loadOrSkip().fixtures();
        return fixtures.stream().map(fx -> DynamicTest.dynamicTest(
                "parse-fidelity: " + fx.name(),
                () -> {
                    if (KnownFidelityGaps.contains(fx.number())) {
                        assertKnownGapStillFails(fx);
                    } else {
                        assertRoundTrips(fx);
                    }
                }));
    }

    private static void assertRoundTrips(FixtureCase fx) {
        JsonValue expected = fx.json();
        JsonValue actual = JsonValue.parse(fx.imposter().toJson());
        assertTrue(
                JsonValue.semanticEquals(expected, actual),
                () -> "fixture " + fx.file().getFileName() + " lost fidelity through fromJson→toJson at "
                        + JsonDiff.firstDifference(expected, actual).orElse("(no raw diff located)"));
    }

    /**
     * A tracked fidelity gap must <em>stay</em> broken until core is fixed: if it starts round-tripping
     * cleanly, this flips red so the entry is removed from {@link KnownFidelityGaps} and the fixture
     * joins the enforced set — a gap can never silently become a pass.
     */
    private static void assertKnownGapStillFails(FixtureCase fx) {
        String tracker = KnownFidelityGaps.tracker(fx.number()).orElse("(untracked)");
        assertFalse(
                roundTripsCleanly(fx),
                () -> "fixture " + fx.file().getFileName() + " now round-trips cleanly — core fidelity gap "
                        + tracker + " appears FIXED. Remove it from KnownFidelityGaps so the parse gate enforces it.");
    }

    private static boolean roundTripsCleanly(FixtureCase fx) {
        try {
            return JsonValue.semanticEquals(fx.json(), JsonValue.parse(fx.imposter().toJson()));
        } catch (WireFormatException expectedGap) {
            // A tracked gap where the wire model can't yet parse the fixture (e.g. #55's copy/wait
            // forms) throws a typed WireFormatException — that is the "still gapped" signal. Any OTHER
            // RuntimeException (an NPE from an unrelated regression, say) is NOT swallowed: it
            // propagates as a test error so a new bug can't hide behind a known gap.
            return false;
        }
    }

    /**
     * AC6 — the gate must actually catch drift. A fixture mutated at one modeled value is no longer
     * {@code semanticEquals} to the canonical form, and {@link JsonDiff} names the exact path.
     */
    @Test
    void mutatedFixtureFailsWithPathPreciseDiff() {
        String canonical = """
                {"protocol":"http","port":4501,"stubs":[
                  {"predicates":[{"equals":{"method":"GET","path":"/health"}}],
                   "responses":[{"is":{"statusCode":200,"body":"OK"}}]}]}
                """;
        String mutated = canonical.replace("\"body\":\"OK\"", "\"body\":\"DOWN\"");

        JsonValue expected = JsonValue.parse(ImposterDefinition.fromJson(canonical).toJson());
        JsonValue broken = JsonValue.parse(ImposterDefinition.fromJson(mutated).toJson());

        assertFalse(JsonValue.semanticEquals(expected, broken),
                "a mutated response body must not be semantically equal to the canonical fixture");

        Optional<String> diff = JsonDiff.firstDifference(expected, broken);
        assertTrue(diff.isPresent(), "a divergence must be locatable");
        assertTrue(diff.get().contains("stubs[0].responses[0].is.body"),
                () -> "diff must be path-precise, was: " + diff.get());
        assertTrue(diff.get().contains("OK") && diff.get().contains("DOWN"),
                () -> "diff must show both values, was: " + diff.get());
    }
}
