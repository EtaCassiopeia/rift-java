package io.github.etacassiopeia.rift.conformance;

import io.github.etacassiopeia.rift.dsl.RiftDsl;
import io.github.etacassiopeia.rift.dsl.Script;
import io.github.etacassiopeia.rift.dsl.StubSpec;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.model.ImposterDefinition;
import io.github.etacassiopeia.rift.model.Stub;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static io.github.etacassiopeia.rift.dsl.RiftDsl.and;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.body;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.contains;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.deepEquals;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.endsWith;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.exists;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.header;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.imposter;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.inMemoryFlowState;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.inject;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.matches;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.method;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.not;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.notExists;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.okJson;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.onDelete;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.onGet;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.onPost;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.onRequest;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.or;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.path;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.query;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.script;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.startsWith;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.status;

/**
 * The hand-maintained DSL-expressibility registry: fixture number → the same imposter rebuilt using
 * <em>only</em> the {@link io.github.etacassiopeia.rift.dsl.RiftDsl} typed API (never {@code
 * ImposterDefinition.fromJson}). {@link DslExpressibilityTest} asserts each entry's serialized form
 * is {@code semanticEquals} to the corpus fixture (modulo the {@code _verify} annotation).
 *
 * <p>Per issue #7's "inexpressible = red build" rule, every fixture that is runnable in this lane
 * (see {@link FixtureCase#runnableInLane()}) MUST have an entry here; a missing entry fails the gate,
 * forcing the DSL to grow with the engine grammar.
 *
 * <p>{@code RiftDsl.equals(...)} is always called fully qualified — an unqualified {@code equals}
 * resolves to {@link Object#equals} instead (see {@code CorpusExpressibilityTest} in core for why).
 */
final class DslFixtures {

    private static final Map<Integer, Supplier<ImposterDefinition>> REGISTRY;

    static {
        Map<Integer, Supplier<ImposterDefinition>> registry = new HashMap<>();
        registry.put(1, DslFixtures::build01);
        registry.put(2, DslFixtures::build02);
        registry.put(5, DslFixtures::build05);
        registry.put(6, DslFixtures::build06);
        registry.put(10, DslFixtures::build10);
        registry.put(11, DslFixtures::build11);
        registry.put(13, DslFixtures::build13);
        registry.put(15, DslFixtures::build15);
        registry.put(17, DslFixtures::build17);
        REGISTRY = Map.copyOf(registry);
    }

    private DslFixtures() {}

    static boolean has(int fixtureNumber) {
        return REGISTRY.containsKey(fixtureNumber);
    }

    static Optional<ImposterDefinition> build(int fixtureNumber) {
        return Optional.ofNullable(REGISTRY.get(fixtureNumber)).map(Supplier::get);
    }

    /**
     * Tags a built stub with the corpus's per-stub {@code name} annotation. {@link Stub} has no
     * modeled {@code name} field ({@code StubSpec} has no setter for one either) — the fixtures'
     * per-stub names are carried purely as an unknown/extension wire key, so the typed way to attach
     * one is {@link Stub}'s own generic {@code withExtra}, the same escape hatch {@link
     * ImposterDefinition#withExtra} and {@code IsResponse#withExtra} use for their own unmodeled keys.
     */
    private static Stub named(String name, StubSpec stub) {
        return stub.build().withExtra("name", new JsonString(name));
    }

    private static ImposterDefinition build01() {
        return imposter("01 · Basic REST API")
                .port(4501)
                .protocol("http")
                .record()
                .defaultResponse(status(404)
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody("{\"error\":\"not found\",\"hint\":\"see README for the route list\"}"))
                .stub(List.of(
                        named("Health check",
                                onGet("/health").willReturn(status(200).withTextBody("OK"))),
                        named("List products (JSON array body)",
                                onGet("/api/products").willReturn(okJson(
                                        "[{\"id\":123,\"name\":\"Widget\",\"price\":9.99},{\"id\":456,\"name\":\"Gadget\",\"price\":19.99}]"))),
                        named("Get one product by numeric id (regex path)",
                                onRequest()
                                        .withPredicate(and(method(RiftDsl.equals("GET")), path(matches("^/api/products/\\d+$"))))
                                        .willReturn(okJson("{\"id\":123,\"name\":\"Widget\",\"price\":9.99}"))),
                        named("Create product",
                                onPost("/api/products").willReturn(status(201)
                                        .withHeader("Content-Type", "application/json")
                                        .withHeader("Location", "/api/products/999")
                                        .withJsonBody("{\"id\":999,\"message\":\"created\"}"))),
                        named("Delete product (no content)",
                                onRequest()
                                        .withPredicate(and(method(RiftDsl.equals("DELETE")), path(matches("^/api/products/\\d+$"))))
                                        .willReturn(status(204))),
                        named("Response cycling — three responses served round-robin",
                                onGet("/api/rotating")
                                        .willReturn(status(200).withJsonBody("{\"served\":\"first\"}"))
                                        .willReturn(status(200).withJsonBody("{\"served\":\"second\"}"))
                                        .willReturn(status(200).withJsonBody("{\"served\":\"third\"}")))))
                .build();
    }

    private static ImposterDefinition build02() {
        return imposter("02 · Predicate Showcase")
                .port(4502)
                .protocol("http")
                .record()
                .defaultResponse(status(400)
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody("{\"error\":\"no predicate matched\",\"hint\":\"see README §02 for matching requests\"}"))
                .stub(List.of(
                        named("equals — exact path + method",
                                onGet("/eq").willReturn(status(200).withJsonBody("{\"matched\":\"equals\"}"))),
                        named("equals caseSensitive=false — /CaSe matches /case",
                                onRequest()
                                        .withPredicate(path(RiftDsl.equals("/case")).caseSensitive(false))
                                        .willReturn(status(200).withJsonBody("{\"matched\":\"equals-caseInsensitive\"}"))),
                        named("deepEquals — query must be EXACTLY {a:1}",
                                onRequest()
                                        .withPredicate(query("a", deepEquals("1")).caseSensitive(true))
                                        .willReturn(status(200).withJsonBody("{\"matched\":\"deepEquals\"}"))),
                        named("contains — body contains 'needle'",
                                onRequest()
                                        .withBody(contains("needle"))
                                        .willReturn(status(200).withJsonBody("{\"matched\":\"contains\"}"))),
                        named("startsWith — path starts with /start",
                                onRequest()
                                        .withPath(startsWith("/start"))
                                        .willReturn(status(200).withJsonBody("{\"matched\":\"startsWith\"}"))),
                        named("endsWith — path ends with /end",
                                onRequest()
                                        .withPath(endsWith("/end"))
                                        .willReturn(status(200).withJsonBody("{\"matched\":\"endsWith\"}"))),
                        named("matches — regex path /id/<digits>",
                                onRequest()
                                        .withPath(matches("^/id/[0-9]+$"))
                                        .willReturn(status(200).withJsonBody("{\"matched\":\"matches\"}"))),
                        named("exists — Authorization header present",
                                onRequest()
                                        .withHeader("Authorization", exists())
                                        .willReturn(status(200).withJsonBody("{\"matched\":\"exists-header\"}"))),
                        named("or — path is /red OR /blue",
                                onRequest()
                                        .withPredicate(or(path(RiftDsl.equals("/red")), path(RiftDsl.equals("/blue"))))
                                        .willReturn(status(200).withJsonBody("{\"matched\":\"or\"}"))),
                        named("not — path is /open and has NO Authorization header",
                                onRequest()
                                        .withPredicate(and(path(RiftDsl.equals("/open")), not(header("Authorization", exists()))))
                                        .willReturn(status(200).withJsonBody("{\"matched\":\"not\"}"))),
                        named("jsonpath — JSON body where $.type == 'order'",
                                onRequest()
                                        .withPredicate(body(RiftDsl.equals("order")).jsonPath("$.type"))
                                        .willReturn(status(200).withJsonBody("{\"matched\":\"jsonpath\"}"))),
                        named("xpath — XML body where //user/@role == 'admin'",
                                onRequest()
                                        .withPredicate(body(RiftDsl.equals("admin")).xPath("string(//user/@role)"))
                                        .willReturn(status(200).withJsonBody("{\"matched\":\"xpath\"}")))))
                .build();
    }

    private static ImposterDefinition build05() {
        return imposter("05 · Scripting Engines (rhai, javascript)")
                .port(4505)
                .protocol("http")
                .record()
                .flowState(inMemoryFlowState().ttl(Duration.ofSeconds(600)))
                .stub(List.of(
                        named("Rhai — stateful counter (ctx.state)",
                                onGet("/rhai/counter").willReturn(script(Script.rhai(
                                        "fn respond(ctx) { let count = ctx.state.incr_by(\"count\", 1); http(200, #{engine: \"rhai\", count: count}) }")))),
                        named("JavaScript inject — counter [needs --allow-injection]",
                                onGet("/js/counter").willReturn(inject(
                                        "function (config, state) { state.count = (state.count || 0) + 1; return { statusCode: 200, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ engine: 'javascript', count: state.count }) }; }"))),
                        named("JavaScript inject — echo the request [needs --allow-injection]",
                                onPost("/js/echo").willReturn(inject(
                                        "function (config, state) { var req = config.request || config; return { statusCode: 200, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ engine: 'javascript', method: req.method, path: req.path, body: req.body }) }; }"))),
                        named("Health — lists the engines this imposter exercises",
                                onGet("/health").willReturn(okJson("{\"status\":\"ok\",\"engines\":[\"rhai\",\"javascript\"]}")))))
                .build();
    }

    private static ImposterDefinition build06() {
        return imposter("06 · Stateful Retry Simulation (flow state)")
                .port(4506)
                .protocol("http")
                .record()
                .flowState(inMemoryFlowState().flowIdFromHeader("X-Flow-Id"))
                .stub(List.of(
                        named("Fail the first 2 attempts with 503, succeed on the 3rd (keyed by X-Flow-Id)",
                                onGet("/api/resource").willReturn(script(Script.rhai(
                                        "fn respond(ctx) { let attempts = ctx.state.incr_by(\"attempts\", 1); if attempts <= 2 { http(503, #{error: \"service unavailable\", attempt: attempts}).header(\"Retry-After\", \"1\").header(\"X-Attempt\", `${attempts}`) } else { http(200, #{ok: true, succeededOnAttempt: attempts}).header(\"X-Attempt\", `${attempts}`) } }")))),
                        named("Inspect the current attempt count for a flow",
                                onGet("/api/attempts").willReturn(script(Script.rhai(
                                        "fn respond(ctx) { let flow_id = ctx.request.header(\"x-flow-id\"); if flow_id == () { flow_id = \"default\"; }; let attempts = ctx.state.get_or(\"attempts\", 0); http(200, #{flowId: flow_id, attempts: attempts}) }")))),
                        named("Reset the attempt counter for a flow",
                                onDelete("/api/reset").willReturn(script(Script.rhai(
                                        "fn respond(ctx) { let flow_id = ctx.request.header(\"x-flow-id\"); if flow_id == () { flow_id = \"default\"; }; ctx.state.delete(\"attempts\"); http(200, #{message: \"reset\", flowId: flow_id}) }"))))))
                .build();
    }

    private static ImposterDefinition build10() {
        return imposter("10 · Correlated isolation (one port, partitioned by a header)")
                .port(4510)
                .protocol("http")
                .record()
                .flowState(inMemoryFlowState().flowIdFromHeader("X-Mock-Space"))
                .stub(List.of(
                        named("space=alice — only matches requests with X-Mock-Space: alice",
                                onRequest().withPath(RiftDsl.equals("/data")).inSpace("alice")
                                        .willReturn(status(200).withJsonBody("{\"owner\":\"alice\",\"items\":[1,2]}"))),
                        named("space=bob — isolated from alice",
                                onRequest().withPath(RiftDsl.equals("/data")).inSpace("bob")
                                        .willReturn(status(200).withJsonBody("{\"owner\":\"bob\",\"items\":[9]}"))),
                        named("global (no space) — matches any caller",
                                onRequest().withPath(RiftDsl.equals("/health"))
                                        .willReturn(status(200).withTextBody("OK")))))
                .build();
    }

    private static ImposterDefinition build11() {
        return imposter("11 · Multi-value headers, date templates, defaultResponse/defaultForward, CORS")
                .port(4511)
                .protocol("http")
                .allowCors()
                .defaultForward("http://localhost:4501")
                .stub(List.of(
                        named("multi-value headers — two Set-Cookie lines on the wire (#238)",
                                onRequest().withPath(RiftDsl.equals("/cookies"))
                                        .willReturn(status(200)
                                                .withHeader("Set-Cookie", "sessionId=abc", "theme=dark")
                                                .withHeader("X-Single", "one")
                                                .withTextBody("two cookies set"))),
                        named("serve-time date templates — {{DAYS+N}}/{{MONTHS+N}}/{{NOW}} (#195)",
                                onRequest().withPath(RiftDsl.equals("/token"))
                                        .willReturn(status(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withTextBody("{\"issued\":\"{{NOW}}\",\"expires\":\"{{DAYS+30}}\",\"renewal\":\"{{MONTHS+12}}\"}")))))
                .build();
    }

    private static ImposterDefinition build13() {
        return imposter("13 · decorate conventions — Mountebank JS `config =>` and Rhai (#191)")
                .port(4513)
                .protocol("http")
                .stub(List.of(
                        named("Mountebank JS arrow: config => { config.response... }",
                                onRequest().withPath(RiftDsl.equals("/js-arrow"))
                                        .willReturn(status(200).withTextBody("{\"v\":1}")
                                                .decorate("config => { config.response.headers['X-Decorated-By'] = 'config-arrow'; config.response.statusCode = 202; }"))),
                        named("Mountebank JS function(config) form — reads config.request",
                                onRequest().withPath(RiftDsl.equals("/js-fn"))
                                        .willReturn(status(200).withTextBody("replaced")
                                                .decorate("function(config) { config.response.body = 'method-was-' + config.request.method; }"))),
                        named("Rhai decorate (Rift-native) — unchanged",
                                onRequest().withPath(RiftDsl.equals("/rhai"))
                                        .willReturn(status(200).withTextBody("orig")
                                                .decorate("response.body = \"rhai-\" + request.path;")))))
                .build();
    }

    private static ImposterDefinition build15() {
        return imposter("15 · Predicate modifiers (issue #254 §D1)")
                .port(4515)
                .protocol("http")
                .record()
                .defaultResponse(status(404)
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody("{\"error\":\"no predicate matched\"}"))
                .stub(List.of(
                        named("contains — body contains 'needle'",
                                onRequest().withBody(contains("needle"))
                                        .willReturn(status(200).withTextBody("contains"))),
                        named("endsWith — path ends with /end",
                                onRequest().withPath(endsWith("/end"))
                                        .willReturn(status(200).withTextBody("endsWith"))),
                        named("except — strip digits before comparing path to /v",
                                onRequest().withPredicate(path(RiftDsl.equals("/v")).except("[0-9]+"))
                                        .willReturn(status(200).withTextBody("except"))),
                        named("deepEquals — query must be EXACTLY {a:1} (extra keys rejected)",
                                onRequest().withPredicate(query("a", deepEquals("1")))
                                        .willReturn(status(200).withTextBody("deep"))),
                        named("exists:false — header X-Skip must be ABSENT",
                                onRequest().withPredicate(header("X-Skip", notExists())).withPath(RiftDsl.equals("/noskip"))
                                        .willReturn(status(200).withTextBody("absent")))))
                .build();
    }

    private static ImposterDefinition build17() {
        return imposter("17 · Extended faults — error body/headers, latency range (issue #254 §D5)")
                .port(4517)
                .protocol("http")
                .record()
                .stub(List.of(
                        named("error fault — custom status, body and headers",
                                onRequest().withPath(RiftDsl.equals("/err"))
                                        .willReturn(status(200).withErrorFault(1.0, 503, "DOWN", Map.of("Retry-After", "30")))),
                        named("latency fault — random range 300-600ms",
                                onRequest().withPath(RiftDsl.equals("/slow"))
                                        .willReturn(status(200).withTextBody("slow")
                                                .withLatencyFault(1.0, Duration.ofMillis(300), Duration.ofMillis(600))))))
                .build();
    }
}
