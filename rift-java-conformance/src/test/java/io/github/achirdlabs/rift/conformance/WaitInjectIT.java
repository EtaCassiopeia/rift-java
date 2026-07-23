package io.github.achirdlabs.rift.conformance;

import io.github.achirdlabs.rift.Imposter;
import io.github.achirdlabs.rift.Rift;
import io.github.achirdlabs.rift.SpawnOptions;
import io.github.achirdlabs.rift.dsl.IsSpec;
import io.github.achirdlabs.rift.error.InvalidDefinition;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static io.github.achirdlabs.rift.conformance.LiveEngine.integrationEnabled;
import static io.github.achirdlabs.rift.dsl.RiftDsl.imposter;
import static io.github.achirdlabs.rift.dsl.RiftDsl.okJson;
import static io.github.achirdlabs.rift.dsl.RiftDsl.onGet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end coverage for a <em>function</em> {@code wait} against a live engine — the gap that let
 * rift#608 go unnoticed. rift-java emits {@code _behaviors.wait} in two spellings: the object form
 * {@code {"inject": "function(){...}"}} ({@link IsSpec#waitInject}) and the Mountebank-compatible
 * bare string ({@link IsSpec#waitScript}). rift#608 ruled <b>both canonical</b> and the engine gained
 * a matching {@code WaitBehavior::Inject} variant.
 *
 * <p>Why this must be end-to-end: the pre-ruling failure was <em>not</em> a load error. The engine
 * parsed {@code _behaviors} into a cache and swallowed the failure, so an imposter using the object
 * form started cleanly and served with the <b>entire {@code _behaviors} block dropped</b> — no wait,
 * silently. Every client-side round-trip test still passed, which is precisely why the drift went
 * unnoticed. Only observing real latency from a real engine can catch it.
 *
 * <p>Gated to {@link ConformanceTransport#SPAWN}: {@code --allow-injection} is a spawn-CLI flag
 * ({@code RiftProcess.buildCommand}) with no embedded equivalent, so the injection gate below is
 * only expressible on this lane. Needs no corpus — just {@code RIFT_IT=1} and a resolvable engine.
 */
class WaitInjectIT {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** A function wait returning a delay well above scheduling noise but short enough to keep CI quick. */
    private static final long REQUESTED_MS = 600;
    private static final String WAIT_SCRIPT = "function () { return " + REQUESTED_MS + "; }";

    /**
     * The defect being guarded is "the wait never ran" (a dropped block serves in ~0 ms), so half the
     * requested delay separates applied from dropped with a wide margin against CI jitter. Asserting
     * near REQUESTED_MS would buy no extra defect-detection and would flake.
     */
    private static final long MIN_OBSERVED_MS = REQUESTED_MS / 2;

    /** The two canonical spellings of a function wait — one injection capability, two syntaxes. */
    private static Map<String, UnaryOperator<IsSpec>> functionWaitForms() {
        Map<String, UnaryOperator<IsSpec>> forms = new LinkedHashMap<>();
        forms.put("object form {\"inject\": ...}", spec -> spec.waitInject(WAIT_SCRIPT));
        forms.put("bare-string form", spec -> spec.waitScript(WAIT_SCRIPT));
        return forms;
    }

    /** Waits that are not an injection surface: a literal delay, and a degenerate range of one. */
    private static Map<String, UnaryOperator<IsSpec>> nonFunctionWaitForms() {
        Map<String, UnaryOperator<IsSpec>> forms = new LinkedHashMap<>();
        forms.put("fixed form", spec -> spec.waitMs(REQUESTED_MS));
        forms.put("range form {\"min\",\"max\"}", spec -> spec.waitBetween(REQUESTED_MS, REQUESTED_MS));
        return forms;
    }

    /**
     * AC2 — each spelling actually delays the response on a live engine. Also the standing gate for
     * AC1: on an engine predating the rift#608 fix the block is dropped and this fails, so it pins
     * the engine pin to a release that carries the ruling.
     */
    @TestFactory
    Stream<DynamicTest> functionWaitAppliesLatencyOnALiveEngine() {
        return eachForm(functionWaitForms(), (name, form) -> {
            try (Rift rift = spawn(true)) {
                Imposter imp = rift.create(waitImposter("wait-inject · applies", form));

                long elapsedMs = timeGet(imp.uri() + "/slow");

                assertTrue(elapsedMs >= MIN_OBSERVED_MS, () -> name + ": the wait was not applied — responded in "
                        + elapsedMs + "ms, expected >= " + MIN_OBSERVED_MS + "ms for a "
                        + REQUESTED_MS + "ms function wait (a silently dropped _behaviors block serves immediately)");
            }
        });
    }

    /**
     * AC3 — a function wait is one capability with two syntaxes, and rift#610 gates both behind
     * {@code --allow-injection}. Without the flag the engine rejects the imposter, surfaced as
     * {@link InvalidDefinition}.
     *
     * <p>The message is asserted, not just the type: {@code InvalidDefinition} is the SDK's mapping
     * for <em>any</em> HTTP 400, so type alone would keep this green on an unrelated rejection while
     * it had silently stopped exercising the injection gate.
     */
    @TestFactory
    Stream<DynamicTest> functionWaitIsRejectedWithoutAllowInjection() {
        return eachForm(functionWaitForms(), (name, form) -> {
            try (Rift rift = spawn(false)) {
                InvalidDefinition rejection = assertThrows(InvalidDefinition.class,
                        () -> rift.create(waitImposter("wait-inject · gated", form)),
                        () -> name + ": a function wait must be rejected when the engine runs without "
                                + "--allow-injection");

                String message = String.valueOf(rejection.getMessage()).toLowerCase(Locale.ROOT);
                assertTrue(message.contains("allowinjection"), () -> name
                        + ": the rejection must come from the injection gate, but was: " + rejection.getMessage());
            }
        });
    }

    /**
     * AC3's boundary — rift#610 gates <em>function</em> waits only; a numeric or {@code {min,max}}
     * wait is not an injection surface and must keep working with the flag off. Without this, an
     * over-broad gate that rejected every wait would satisfy the rejection test above and ship
     * unnoticed.
     */
    @TestFactory
    Stream<DynamicTest> nonFunctionWaitsAreNotGatedByAllowInjection() {
        return eachForm(nonFunctionWaitForms(), (name, form) -> {
            try (Rift rift = spawn(false)) {
                Imposter imp = rift.create(waitImposter("wait-inject · ungated", form));

                long elapsedMs = timeGet(imp.uri() + "/slow");

                assertTrue(elapsedMs >= MIN_OBSERVED_MS, () -> name + ": a non-function wait must still be "
                        + "applied when the engine runs without --allow-injection — responded in " + elapsedMs
                        + "ms, expected >= " + MIN_OBSERVED_MS + "ms");
            }
        });
    }

    private static io.github.achirdlabs.rift.dsl.ImposterSpec waitImposter(
            String name, UnaryOperator<IsSpec> form) {
        return imposter(name)
                .protocol("http")
                .stub(onGet("/slow").willReturn(form.apply(okJson("{\"ok\":true}"))));
    }

    private static Rift spawn(boolean allowInjection) {
        return Rift.spawn(SpawnOptions.builder().allowInjection(allowInjection).build());
    }

    private static long timeGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20)).GET().build();
        long startedAt = System.nanoTime();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        assertEquals(200, response.statusCode(), "the stub must serve — a wait must delay it, not break it");
        return elapsedMs;
    }

    /**
     * Runs {@code body} as one dynamic test per form. The two skip conditions are reported separately
     * so a lane that silently lost {@code RIFT_IT} is distinguishable from one that simply isn't the
     * SPAWN lane — this gate evaporating unnoticed is the failure mode it exists to prevent.
     */
    private static Stream<DynamicTest> eachForm(Map<String, UnaryOperator<IsSpec>> forms, WaitFormCase body) {
        return forms.entrySet().stream().map(form -> DynamicTest.dynamicTest(form.getKey(), () -> {
            assumeTrue(integrationEnabled(), "set RIFT_IT=1 to run the live-engine wait lane");
            assumeTrue(ConformanceTransport.selected() == ConformanceTransport.SPAWN,
                    "--allow-injection is a spawn-CLI flag with no embedded equivalent; SPAWN lane only");
            body.run(form.getKey(), form.getValue());
        }));
    }

    @FunctionalInterface
    private interface WaitFormCase {
        void run(String name, UnaryOperator<IsSpec> form) throws Exception;
    }
}
