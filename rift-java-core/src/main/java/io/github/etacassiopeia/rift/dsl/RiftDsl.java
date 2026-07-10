package io.github.etacassiopeia.rift.dsl;

import io.github.etacassiopeia.rift.json.JsonBool;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.Predicate;
import io.github.etacassiopeia.rift.model.PredicateOperation;
import io.github.etacassiopeia.rift.model.PredicateParameters;
import io.github.etacassiopeia.rift.model.Response;
import io.github.etacassiopeia.rift.model.RiftResponseExtension;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A fluent, static-import DSL over the typed Rift/Mountebank wire model
 * ({@code io.github.etacassiopeia.rift.model}).
 *
 * <p>Every method here builds {@code model.*} values directly — none of them parse JSON. The
 * intended use is {@code import static io.github.etacassiopeia.rift.dsl.RiftDsl.*;}, then compose
 * an imposter as a single expression:
 *
 * <pre>{@code
 * ImposterDefinition imposter = imposter("Basic REST API")
 *         .port(4545)
 *         .stub(onGet("/health").willReturn(ok().withTextBody("OK")))
 *         .build();
 * }</pre>
 *
 * <p>The grammar has three parts: imposter/stub construction ({@link #imposter(String)}, {@link
 * #onGet(String)} and siblings), predicate matchers and field binders ({@link #equals(String)} and
 * siblings, {@link #method(Matcher)} and siblings, {@link #and(PredicateSpec...)} and siblings),
 * and response construction ({@link #ok()} and siblings, {@link #proxyTo(String)}, {@link
 * #fault(Fault)}, {@link #inject(String)}, {@link #script(Script)}). {@link #scenario(String)}
 * builds a scenario finite-state machine as a list of stubs.
 */
public final class RiftDsl {

    private RiftDsl() {}

    // ------------------------------------------------------------------
    // Imposter / stub construction
    // ------------------------------------------------------------------

    /** Starts building an imposter with the given {@code name}. */
    public static ImposterSpec imposter(String name) {
        return new ImposterSpec(name);
    }

    /** A stub seeded with a combined {@code equals} predicate on {@code method: "GET"} and {@code path}. */
    public static StubSpec onGet(String path) {
        return on("GET", path);
    }

    /** A stub seeded with a combined {@code equals} predicate on {@code method: "POST"} and {@code path}. */
    public static StubSpec onPost(String path) {
        return on("POST", path);
    }

    /** A stub seeded with a combined {@code equals} predicate on {@code method: "PUT"} and {@code path}. */
    public static StubSpec onPut(String path) {
        return on("PUT", path);
    }

    /** A stub seeded with a combined {@code equals} predicate on {@code method: "DELETE"} and {@code path}. */
    public static StubSpec onDelete(String path) {
        return on("DELETE", path);
    }

    /** A stub seeded with a combined {@code equals} predicate on {@code method: "PATCH"} and {@code path}. */
    public static StubSpec onPatch(String path) {
        return on("PATCH", path);
    }

    /** A stub seeded with a combined {@code equals} predicate on {@code method: "HEAD"} and {@code path}. */
    public static StubSpec onHead(String path) {
        return on("HEAD", path);
    }

    /** A stub seeded with a combined {@code equals} predicate on {@code method: "OPTIONS"} and {@code path}. */
    public static StubSpec onOptions(String path) {
        return on("OPTIONS", path);
    }

    /** A stub with no seed predicate: build it up entirely with {@code .withXxx(...)} calls. */
    public static StubSpec onRequest() {
        return new StubSpec(List.of());
    }

    /** A stub seeded with a combined {@code equals} predicate on the given {@code method} and {@code path}. */
    public static StubSpec on(String method, String path) {
        Map<String, JsonValue> fields = new LinkedHashMap<>();
        fields.put("method", new JsonString(method));
        fields.put("path", new JsonString(path));
        Predicate seed = new Predicate(PredicateParameters.EMPTY, new PredicateOperation.Equals(fields));
        return new StubSpec(List.of(seed));
    }

    // ------------------------------------------------------------------
    // Matchers
    // ------------------------------------------------------------------

    /** An {@code equals} match against the literal string {@code value}. */
    public static Matcher equals(String value) {
        return Matcher.create(Matcher.Kind.EQUALS, new JsonString(value));
    }

    /** An {@code equals} match against the literal JSON value {@code value} (typically an object or array body). */
    public static Matcher equals(JsonValue value) {
        return Matcher.create(Matcher.Kind.EQUALS, value);
    }

    /**
     * An {@code equals} match — alias for {@link #equals(String)} usable under {@code import static}.
     *
     * <p>A bare {@code equals(...)} call always resolves to the inherited {@link Object#equals(Object)}
     * before a static import is considered, so {@code equals} is the one matcher that cannot be
     * static-imported unqualified. {@code eq} has no such collision; prefer it in DSL code.
     */
    public static Matcher eq(String value) {
        return equals(value);
    }

    /** An {@code equals} match against a literal JSON value — alias for {@link #equals(JsonValue)}. */
    public static Matcher eq(JsonValue value) {
        return equals(value);
    }

    /** A {@code deepEquals} match against the literal string {@code value} (exact match, no partial-object semantics). */
    public static Matcher deepEquals(String value) {
        return Matcher.create(Matcher.Kind.DEEP_EQUALS, new JsonString(value));
    }

    /** A {@code deepEquals} match against the literal JSON value {@code value}. */
    public static Matcher deepEquals(JsonValue value) {
        return Matcher.create(Matcher.Kind.DEEP_EQUALS, value);
    }

    /** A {@code contains} (substring / partial-object) match against the literal string {@code value}. */
    public static Matcher contains(String value) {
        return Matcher.create(Matcher.Kind.CONTAINS, new JsonString(value));
    }

    /** A {@code contains} (partial-object) match against the literal JSON value {@code value}. */
    public static Matcher contains(JsonValue value) {
        return Matcher.create(Matcher.Kind.CONTAINS, value);
    }

    /** A {@code startsWith} match against the literal string prefix {@code value}. */
    public static Matcher startsWith(String value) {
        return Matcher.create(Matcher.Kind.STARTS_WITH, new JsonString(value));
    }

    /** An {@code endsWith} match against the literal string suffix {@code value}. */
    public static Matcher endsWith(String value) {
        return Matcher.create(Matcher.Kind.ENDS_WITH, new JsonString(value));
    }

    /** A {@code matches} (regular expression) match against {@code regex}. */
    public static Matcher matches(String regex) {
        return Matcher.create(Matcher.Kind.MATCHES, new JsonString(regex));
    }

    /** An {@code exists} match requiring the bound field to be present (and, if a string, non-empty). */
    public static Matcher exists() {
        return Matcher.create(Matcher.Kind.EXISTS, JsonBool.TRUE);
    }

    /** An {@code exists} match requiring the bound field to be absent (or, if a string, empty). */
    public static Matcher notExists() {
        return Matcher.create(Matcher.Kind.EXISTS, JsonBool.FALSE);
    }

    // ------------------------------------------------------------------
    // Field binders — bind a Matcher onto a specific predicate field
    // ------------------------------------------------------------------

    /** Binds {@code matcher} to the request method, as a bare-string {@code method} field. */
    public static PredicateSpec method(Matcher matcher) {
        return PredicateSpec.of(matcher.toOperation(Map.of("method", matcher.value())));
    }

    /** Binds {@code matcher} to the request path, as a bare-string {@code path} field. */
    public static PredicateSpec path(Matcher matcher) {
        return PredicateSpec.of(matcher.toOperation(Map.of("path", matcher.value())));
    }

    /** Binds {@code matcher} to the named request header, nested under the {@code headers} field. */
    public static PredicateSpec header(String name, Matcher matcher) {
        Map<String, JsonValue> fields = Map.of("headers", new JsonObject(Map.of(name, matcher.value())));
        return PredicateSpec.of(matcher.toOperation(fields));
    }

    /** Binds an {@code equals} match against {@code value} to the named request header. */
    public static PredicateSpec header(String name, String value) {
        return header(name, equals(value));
    }

    /** Binds {@code matcher} to the named query-string parameter, nested under the {@code query} field. */
    public static PredicateSpec query(String name, Matcher matcher) {
        Map<String, JsonValue> fields = Map.of("query", new JsonObject(Map.of(name, matcher.value())));
        return PredicateSpec.of(matcher.toOperation(fields));
    }

    /** Binds an {@code equals} match against {@code value} to the named query-string parameter. */
    public static PredicateSpec query(String name, String value) {
        return query(name, equals(value));
    }

    /** Binds {@code matcher} to the request body, as the {@code body} field (verbatim). */
    public static PredicateSpec body(Matcher matcher) {
        return PredicateSpec.of(matcher.toOperation(Map.of("body", matcher.value())));
    }

    // ------------------------------------------------------------------
    // Combinators over whole predicates
    // ------------------------------------------------------------------

    /** The logical AND of the given predicates: all must match. */
    public static PredicateSpec and(PredicateSpec... predicates) {
        return PredicateSpec.of(new PredicateOperation.And(built(predicates)));
    }

    /** The logical OR of the given predicates: at least one must match. */
    public static PredicateSpec or(PredicateSpec... predicates) {
        return PredicateSpec.of(new PredicateOperation.Or(built(predicates)));
    }

    /** The logical negation of the given predicate. */
    public static PredicateSpec not(PredicateSpec predicate) {
        return PredicateSpec.of(new PredicateOperation.Not(predicate.build()));
    }

    private static List<Predicate> built(PredicateSpec... predicates) {
        return Arrays.stream(predicates).map(PredicateSpec::build).toList();
    }

    // ------------------------------------------------------------------
    // Responses
    // ------------------------------------------------------------------

    /** A bare 200 response, with no headers or body. */
    public static ResponseSpec ok() {
        return ResponseSpec.is("200");
    }

    /** A 200 response with a {@code Content-Type: application/json} header and the given JSON body. */
    public static ResponseSpec okJson(JsonValue body) {
        return ResponseSpec.is("200").withHeader("Content-Type", "application/json").withJsonBody(body);
    }

    /** A 200 response with a {@code Content-Type: application/json} header and the body parsed from {@code jsonText}. */
    public static ResponseSpec okJson(String jsonText) {
        return okJson(json(jsonText));
    }

    /** A bare 201 response, with no headers or body. */
    public static ResponseSpec created() {
        return ResponseSpec.is("201");
    }

    /** A bare 204 response, with no headers or body. */
    public static ResponseSpec noContent() {
        return ResponseSpec.is("204");
    }

    /** A bare 404 response, with no headers or body. */
    public static ResponseSpec notFound() {
        return ResponseSpec.is("404");
    }

    /** A bare response at the given status code, with no headers or body. */
    public static ResponseSpec status(int code) {
        return ResponseSpec.is(Integer.toString(code));
    }

    /** Starts building a proxy response forwarding matched requests to {@code url}. */
    public static ProxySpec proxyTo(String url) {
        return ProxySpec.to(url);
    }

    /** A raw connection-fault response, e.g. {@link Fault#CONNECTION_RESET_BY_PEER}. */
    public static ResponseSpec fault(Fault fault) {
        return ResponseSpec.prebuilt(new Response.Fault(fault.wireValue()));
    }

    /** A response entirely computed by the given inline JavaScript function (Mountebank's {@code inject} response). */
    public static ResponseSpec inject(String javascript) {
        return ResponseSpec.prebuilt(new Response.Inject(javascript));
    }

    /** A response entirely generated by a {@code _rift} script, with no {@code is} block. */
    public static ResponseSpec script(Script script) {
        RiftResponseExtension extension = new RiftResponseExtension(Optional.empty(), Optional.of(script.toConfig()), false);
        return ResponseSpec.prebuilt(new Response.RiftScript(extension));
    }

    // ------------------------------------------------------------------
    // Scenario FSM
    // ------------------------------------------------------------------

    /** Starts building a scenario finite-state machine named {@code name}. */
    public static ScenarioSpec scenario(String name) {
        return new ScenarioSpec(name);
    }

    // ------------------------------------------------------------------
    // Misc
    // ------------------------------------------------------------------

    /** Parses {@code text} as JSON, for embedding literal request/response body content in DSL calls. */
    public static JsonValue json(String text) {
        return JsonValue.parse(text);
    }
}
