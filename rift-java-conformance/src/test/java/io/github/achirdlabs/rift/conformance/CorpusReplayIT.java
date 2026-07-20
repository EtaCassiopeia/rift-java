package io.github.achirdlabs.rift.conformance;

import io.github.achirdlabs.rift.Rift;
import io.github.achirdlabs.rift.model.ImposterDefinition;
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
 * Gates 3 &amp; 4 — engine replay over a selectable transport ({@link ConformanceTransport}, default
 * SPAWN; EMBEDDED via {@code -DCONFORMANCE_TRANSPORT}). Starts a real rift engine (pinned to the
 * corpus's {@code engineVersion}, {@code allowInjection} on so {@code injection} fixtures serve), then
 * for every runnable fixture:
 *
 * <ul>
 *   <li><b>raw replay</b> — {@code rift.create(fixtureJson)} (the escape hatch: tests the wire, not
 *       the DSL) then drives its {@code _verify} transcripts;</li>
 *   <li><b>DSL replay</b> — {@code rift.create(DslFixtures.build(n))} then drives the <em>same</em>
 *       transcripts, proving the DSL's output is engine-equivalent, not merely JSON-equivalent.</li>
 * </ul>
 *
 * A fixture without {@code _verify} still gets a smoke GET on each form. Each dynamic test's display
 * name carries the transport (e.g. {@code "01 · Basic REST API [EMBEDDED]"}) so a fixture failing only
 * on one transport is reportable as such. Lane-skipped and fidelity-gapped fixtures are aborted with
 * the reason.
 *
 * <p>Heavy (starts a real engine), so it self-skips unless {@code RIFT_IT=1} and a corpus is
 * resolvable. When the selected transport cannot run in this lane (e.g. EMBEDDED with no native
 * library or on a JDK without the FFM artifact), the run fails loudly rather than passing vacuously.
 */
class CorpusReplayIT {

    private static final String HOST = "localhost";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static ConformanceTransport transport;
    private static Corpus corpus;
    private static Rift rift;
    private static TranscriptAssert transcript;

    @BeforeAll
    static void startEngine() {
        assumeTrue(integrationEnabled(),
                "set RIFT_IT=1 (and provide the conformance corpus) to run the engine replay lane");
        corpus = Corpus.loadOrSkip();
        transport = ConformanceTransport.selected();
        if (!transport.isAvailable()) {
            throw new IllegalStateException(transport + " transport is not available in this lane — "
                    + "EMBEDDED needs rift-java-embedded on the classpath (JDK 21+) and a resolvable librift_ffi "
                    + "(set -Drift.ffi.lib / RIFT_FFI_LIB or add a rift-java-natives classifier jar).");
        }
        rift = transport.engine(corpus);
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
                fx.name() + " [" + transport + "]",
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
        int rawPort = rift.create(transport.prepareFixture(fx.rawJson(), corpus)).port();
        driveOrSmoke(fx, fixtureModel, rawPort);

        // Gate 4 — DSL-built form serving the same behavior.
        ImposterDefinition dslModel = DslFixtures.build(fx.number()).orElseThrow(() ->
                new AssertionError("fixture " + fx.number() + " has no DSL expression (inexpressible = red build)"));
        rift.deleteAll();
        int dslPort = rift.create(transport.prepareFixture(dslModel.toJson(), corpus)).port();
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
            return "lane-skip: fixture requires " + fx.requires() + " which the harness does not provision";
        }
        return "fidelity-gap: fixture cannot round-trip through the wire model yet (tracked by "
                + KnownFidelityGaps.tracker(fx.number()).orElse("(untracked)") + ")";
    }

    private static boolean integrationEnabled() {
        String it = System.getenv("RIFT_IT");
        return it != null && !it.isBlank() && !it.equals("0");
    }
}
