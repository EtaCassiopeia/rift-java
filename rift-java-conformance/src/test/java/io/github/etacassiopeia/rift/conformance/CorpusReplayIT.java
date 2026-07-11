package io.github.etacassiopeia.rift.conformance;

import io.github.etacassiopeia.rift.Rift;
import io.github.etacassiopeia.rift.SpawnOptions;
import io.github.etacassiopeia.rift.model.ImposterDefinition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Gates 3 &amp; 4 — engine replay over the remote/spawn transport. Spawns a real rift engine (pinned
 * to the corpus's {@code engineVersion}, {@code workingDir = corpus/} so relative data paths resolve,
 * {@code allowInjection} on so {@code injection} fixtures serve), then for every runnable fixture:
 *
 * <ul>
 *   <li><b>raw replay</b> — {@code rift.create(fixtureJson)} (the escape hatch: tests the wire, not
 *       the DSL) then drives its {@code _verify} transcripts;</li>
 *   <li><b>DSL replay</b> — {@code rift.create(DslFixtures.build(n))} then drives the <em>same</em>
 *       transcripts, proving the DSL's output is engine-equivalent, not merely JSON-equivalent.</li>
 * </ul>
 *
 * A fixture without {@code _verify} still gets a smoke GET on each form. Lane-skipped and
 * fidelity-gapped fixtures are reported as aborted with the reason.
 *
 * <p>Heavy (spawns an engine, downloads its binary on first use), so it self-skips unless
 * {@code RIFT_IT=1} and a corpus is resolvable — matching the repo's other integration lanes.
 */
class CorpusReplayIT {

    private static final String HOST = "localhost";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static Corpus corpus;
    private static Rift rift;
    private static TranscriptAssert transcript;

    @BeforeAll
    static void spawnEngine() {
        assumeTrue(integrationEnabled(),
                "set RIFT_IT=1 (and provide the conformance corpus) to run the engine replay lane");
        corpus = Corpus.loadOrSkip();
        rift = Rift.spawn(SpawnOptions.builder()
                .version(corpus.engineVersion())
                .workingDir(corpus.payloadRoot())
                .allowInjection(true)
                .build());
        transcript = new TranscriptAssert(HTTP);
    }

    @AfterAll
    static void stopEngine() {
        if (rift != null) {
            rift.close();
        }
    }

    @TestFactory
    Stream<DynamicTest> replay() {
        if (!integrationEnabled() || corpus == null) {
            return Stream.of(DynamicTest.dynamicTest("engine replay (skipped)",
                    () -> assumeTrue(false, "engine replay lane not enabled")));
        }
        List<FixtureCase> fixtures = corpus.fixtures();
        return fixtures.stream().map(fx -> DynamicTest.dynamicTest(
                "engine-replay: " + fx.name(),
                () -> {
                    if (!fx.runnableInLane()) {
                        org.junit.jupiter.api.Assumptions.abort(skipReason(fx));
                    }
                    replayOne(fx);
                }));
    }

    private void replayOne(FixtureCase fx) {
        ImposterDefinition fixtureModel = fx.imposter();

        // Gate 3 — raw wire form.
        rift.deleteAll();
        int rawPort = rift.create(fx.rawJson()).port();
        driveOrSmoke(fx, fixtureModel, rawPort);

        // Gate 4 — DSL-built form serving the same behavior.
        ImposterDefinition dslModel = DslFixtures.build(fx.number()).orElseThrow(() ->
                new AssertionError("fixture " + fx.number() + " has no DSL expression (inexpressible = red build)"));
        rift.deleteAll();
        int dslPort = rift.create(dslModel).port();
        driveOrSmoke(fx, fixtureModel, dslPort);
    }

    private void driveOrSmoke(FixtureCase fx, ImposterDefinition verifySource, int port) {
        if (fx.hasVerify()) {
            transcript.replay(verifySource, HOST, port);
        } else {
            transcript.smoke(HOST, port);
        }
    }

    private static String skipReason(FixtureCase fx) {
        if (fx.skippedInLane()) {
            return "lane-skip: fixture requires " + fx.requires() + " which the remote/spawn lane does not provide";
        }
        return "fidelity-gap: fixture cannot round-trip through the wire model yet (tracked by "
                + KnownFidelityGaps.tracker(fx.number()).orElse("(untracked)") + ")";
    }

    private static boolean integrationEnabled() {
        String it = System.getenv("RIFT_IT");
        return it != null && !it.isBlank() && !it.equals("0");
    }
}
