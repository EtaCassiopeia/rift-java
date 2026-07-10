package io.github.etacassiopeia.rift.dsl;

import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.Behavior;
import io.github.etacassiopeia.rift.model.Behaviors;
import io.github.etacassiopeia.rift.model.IsResponse;
import io.github.etacassiopeia.rift.model.Response;
import io.github.etacassiopeia.rift.model.ResponseMode;
import io.github.etacassiopeia.rift.model.RiftFaultConfig;
import io.github.etacassiopeia.rift.model.RiftResponseExtension;
import io.github.etacassiopeia.rift.model.RiftScriptConfig;
import io.github.etacassiopeia.rift.model.WaitSpec;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A stub response under construction.
 *
 * <p>Most instances build a literal ("is") response: status code, headers, body, behaviors
 * ({@code wait}/{@code decorate}/{@code repeat}), and the opt-in {@code _rift} extensions
 * ({@code fault}/{@code script}/{@code templated}). These are created via {@link RiftDsl#ok()} and
 * its siblings, and every chain method on this class ({@link #withHeader}, {@link #after}, {@link
 * #template}, ...) applies to that shape.
 *
 * <p>A smaller number of instances instead wrap an already-finished non-"is" {@link Response}
 * (built by {@link RiftDsl#fault(Fault)}, {@link RiftDsl#inject(String)}, {@link
 * RiftDsl#script(Script)}, or {@link Fault#latencySpike(Duration)}): those response shapes carry no
 * behaviors or body of their own in the wire model, so the chain methods below reject being called
 * on one of them.
 *
 * <p>Instances are immutable: every chain method returns a new {@code ResponseSpec}. The terminal
 * {@link #build()} produces the {@link Response} model value.
 */
public final class ResponseSpec {

    private final Response prebuilt;
    private final String statusCode;
    private final Map<String, List<String>> headers;
    private final Optional<JsonValue> body;
    private final ResponseMode mode;
    private final List<Behavior> behaviors;
    private final Optional<RiftFaultConfig> fault;
    private final Optional<RiftScriptConfig> script;
    private final boolean templated;

    private ResponseSpec(
            Response prebuilt,
            String statusCode,
            Map<String, List<String>> headers,
            Optional<JsonValue> body,
            ResponseMode mode,
            List<Behavior> behaviors,
            Optional<RiftFaultConfig> fault,
            Optional<RiftScriptConfig> script,
            boolean templated) {
        this.prebuilt = prebuilt;
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
        this.mode = mode;
        this.behaviors = behaviors;
        this.fault = fault;
        this.script = script;
        this.templated = templated;
    }

    /** A fresh "is" response builder at the given status code, with no headers/body/behaviors yet. */
    static ResponseSpec is(String statusCode) {
        return new ResponseSpec(
                null, statusCode, Map.of(), Optional.empty(), ResponseMode.TEXT, List.of(),
                Optional.empty(), Optional.empty(), false);
    }

    /** Wraps an already-finished non-"is" response (fault/inject/script), with no further chaining available. */
    static ResponseSpec prebuilt(Response response) {
        return new ResponseSpec(
                response, IsResponse.DEFAULT_STATUS_CODE, Map.of(), Optional.empty(), ResponseMode.TEXT, List.of(),
                Optional.empty(), Optional.empty(), false);
    }

    /** An "is" response at status 200 with a {@code _rift} latency fault attached. */
    static ResponseSpec withLatencyFault(RiftFaultConfig config) {
        return new ResponseSpec(
                null, IsResponse.DEFAULT_STATUS_CODE, Map.of(), Optional.empty(), ResponseMode.TEXT, List.of(),
                Optional.of(config), Optional.empty(), false);
    }

    /**
     * Adds or replaces a response header. Repeatable — each call adds another header, so a response
     * needing several headers chains this method once per header.
     *
     * @throws IllegalStateException if this spec wraps a non-"is" response
     */
    public ResponseSpec withHeader(String name, String... values) {
        requireIsResponse();
        Map<String, List<String>> next = new LinkedHashMap<>(headers);
        next.put(name, List.of(values));
        return new ResponseSpec(null, statusCode, next, body, mode, behaviors, fault, script, templated);
    }

    /**
     * Sets the response body to the given JSON value directly.
     *
     * @throws IllegalStateException if this spec wraps a non-"is" response
     */
    public ResponseSpec withJsonBody(JsonValue value) {
        requireIsResponse();
        return new ResponseSpec(null, statusCode, headers, Optional.of(value), mode, behaviors, fault, script, templated);
    }

    /**
     * Sets the response body by parsing {@code jsonText} as JSON.
     *
     * @throws IllegalStateException if this spec wraps a non-"is" response
     */
    public ResponseSpec withJsonBody(String jsonText) {
        return withJsonBody(RiftDsl.json(jsonText));
    }

    /**
     * Sets the response body to the literal text {@code text} (wrapped as a JSON string, per
     * Mountebank's text-mode body convention).
     *
     * @throws IllegalStateException if this spec wraps a non-"is" response
     */
    public ResponseSpec withTextBody(String text) {
        requireIsResponse();
        return new ResponseSpec(null, statusCode, headers, Optional.of(new JsonString(text)), mode, behaviors, fault, script, templated);
    }

    /**
     * Delays the response by the given duration (a {@code wait} behavior with a fixed delay).
     *
     * @throws IllegalStateException if this spec wraps a non-"is" response
     */
    public ResponseSpec after(Duration duration) {
        return waitMs(duration.toMillis());
    }

    /**
     * Delays the response by a fixed number of milliseconds (a {@code wait} behavior).
     *
     * <p>Named {@code waitMs} rather than {@code wait}: {@code wait(long)} would silently attempt to
     * override the {@code final} {@link Object#wait(long)} and fail to compile.
     *
     * @throws IllegalStateException if this spec wraps a non-"is" response
     */
    public ResponseSpec waitMs(long milliseconds) {
        return withBehavior(new Behavior.Wait(new WaitSpec.Fixed(milliseconds)));
    }

    /**
     * Delays the response by a random duration in {@code [minMs, maxMs]} (a {@code wait} behavior
     * with a range).
     *
     * @throws IllegalStateException if this spec wraps a non-"is" response
     */
    public ResponseSpec waitBetween(long minMs, long maxMs) {
        return withBehavior(new Behavior.Wait(new WaitSpec.Range(minMs, maxMs)));
    }

    /**
     * Delays the response by a duration computed by the given script (a {@code wait} behavior whose
     * value is an {@code inject}ed function).
     *
     * @throws IllegalStateException if this spec wraps a non-"is" response
     */
    public ResponseSpec waitInject(String script) {
        return withBehavior(new Behavior.Wait(new WaitSpec.Inject(script)));
    }

    /**
     * Post-processes the response with the given decorator script (a {@code decorate} behavior).
     *
     * @throws IllegalStateException if this spec wraps a non-"is" response
     */
    public ResponseSpec decorate(String script) {
        return withBehavior(new Behavior.Decorate(script));
    }

    /**
     * Serves this response only for the first {@code count} matches, after which the stub's next
     * response takes over (a {@code repeat} behavior).
     *
     * @throws IllegalStateException if this spec wraps a non-"is" response
     */
    public ResponseSpec repeat(int count) {
        return withBehavior(new Behavior.Repeat(count));
    }

    /**
     * Marks this response as templated: its body/headers may contain {@code {{ }}} placeholders the
     * engine resolves against the request (sets {@code _rift.templated}).
     *
     * @throws IllegalStateException if this spec wraps a non-"is" response
     */
    public ResponseSpec template() {
        requireIsResponse();
        return new ResponseSpec(null, statusCode, headers, body, mode, behaviors, fault, script, true);
    }

    private ResponseSpec withBehavior(Behavior behavior) {
        requireIsResponse();
        List<Behavior> next = Stream.concat(behaviors.stream(), Stream.of(behavior)).toList();
        return new ResponseSpec(null, statusCode, headers, body, mode, next, fault, script, templated);
    }

    private void requireIsResponse() {
        if (prebuilt != null) {
            throw new IllegalStateException(
                    "this response is already a terminal " + prebuilt.getClass().getSimpleName()
                            + " response and carries no headers/body/behaviors of its own");
        }
    }

    /** Builds the immutable {@link Response} this spec represents. */
    public Response build() {
        if (prebuilt != null) {
            return prebuilt;
        }
        return new Response.Is(new IsResponse(statusCode, headers, body, mode), new Behaviors(behaviors), riftExtension());
    }

    /**
     * Builds this spec as an imposter-level {@link IsResponse} default response.
     *
     * @throws IllegalStateException if this spec wraps a non-"is" response
     */
    IsResponse buildIsResponse() {
        requireIsResponse();
        return new IsResponse(statusCode, headers, body, mode);
    }

    private Optional<RiftResponseExtension> riftExtension() {
        if (fault.isEmpty() && script.isEmpty() && !templated) {
            return Optional.empty();
        }
        return Optional.of(new RiftResponseExtension(fault, script, templated));
    }
}
