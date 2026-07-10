package io.github.etacassiopeia.rift.dsl;

import io.github.etacassiopeia.rift.json.JsonBool;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.PathRewrite;
import io.github.etacassiopeia.rift.model.ProxyResponse;
import io.github.etacassiopeia.rift.model.Response;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A proxy response under construction, produced by {@link RiftDsl#proxyTo(String)}: forwards the
 * matched request to an upstream URL and, optionally, records the exchange as a new stub.
 *
 * <p>Instances are immutable: every chain method returns a new {@code ProxySpec}. The terminal
 * {@link #build()} produces the {@link Response.Proxy} model value.
 */
public final class ProxySpec implements ResponseSpec {

    private final String to;
    private final String mode;
    private final List<JsonValue> predicateGenerators;
    private final boolean addWaitBehavior;
    private final Map<String, String> injectHeaders;
    private final Optional<String> addDecorateBehavior;
    private final Optional<PathRewrite> pathRewrite;

    private ProxySpec(
            String to,
            String mode,
            List<JsonValue> predicateGenerators,
            boolean addWaitBehavior,
            Map<String, String> injectHeaders,
            Optional<String> addDecorateBehavior,
            Optional<PathRewrite> pathRewrite) {
        this.to = to;
        this.mode = mode;
        this.predicateGenerators = predicateGenerators;
        this.addWaitBehavior = addWaitBehavior;
        this.injectHeaders = injectHeaders;
        this.addDecorateBehavior = addDecorateBehavior;
        this.pathRewrite = pathRewrite;
    }

    /** A fresh proxy targeting {@code url}, with the engine's default proxy mode. */
    static ProxySpec to(String url) {
        return new ProxySpec(url, "", List.of(), false, Map.of(), Optional.empty(), Optional.empty());
    }

    /** Proxies each matching request and records only the first response as a permanent stub. */
    public ProxySpec proxyOnce() {
        return withMode("proxyOnce");
    }

    /** Proxies every matching request, always forwarding live (no recording). */
    public ProxySpec proxyAlways() {
        return withMode("proxyAlways");
    }

    /** Proxies every matching request without ever recording a new stub. */
    public ProxySpec proxyTransparent() {
        return withMode("proxyTransparent");
    }

    private ProxySpec withMode(String newMode) {
        return new ProxySpec(to, newMode, predicateGenerators, addWaitBehavior, injectHeaders, addDecorateBehavior, pathRewrite);
    }

    /** Adds a predicate generator, controlling which parts of a proxied request become a recorded predicate. */
    public ProxySpec withPredicateGenerator(JsonValue generator) {
        List<JsonValue> next = Stream.concat(predicateGenerators.stream(), Stream.of(generator)).toList();
        return new ProxySpec(to, mode, next, addWaitBehavior, injectHeaders, addDecorateBehavior, pathRewrite);
    }

    /** Adds a predicate generator, parsing {@code jsonText} as its JSON definition. */
    public ProxySpec withPredicateGenerator(String jsonText) {
        return withPredicateGenerator(RiftDsl.json(jsonText));
    }

    /** Adds a predicate generator matching the given plain request fields (e.g. {@code method}, {@code path}). */
    public ProxySpec generateBy(RequestField... fields) {
        JsonObject.Builder matches = JsonObject.builder();
        for (RequestField field : Arrays.asList(fields)) {
            matches.put(field.wire(), JsonBool.TRUE);
        }
        JsonObject generator = JsonObject.builder().put("matches", matches.build()).build();
        return withPredicateGenerator(generator);
    }

    /** Adds a predicate generator built from a {@link PredicateGeneratorSpec} (fields plus case-sensitivity/jsonpath knobs). */
    public ProxySpec generateBy(PredicateGeneratorSpec generator) {
        return withPredicateGenerator(generator.build());
    }

    /** Injects the given header into the proxied (upstream) request. Repeatable. */
    public ProxySpec injectHeader(String name, String value) {
        Map<String, String> next = new LinkedHashMap<>(injectHeaders);
        next.put(name, value);
        return new ProxySpec(to, mode, predicateGenerators, addWaitBehavior, next, addDecorateBehavior, pathRewrite);
    }

    /** Rewrites the {@code from} substring of the proxied request's path to {@code to}. */
    public ProxySpec rewritePath(String from, String to) {
        return new ProxySpec(this.to, mode, predicateGenerators, addWaitBehavior, injectHeaders, addDecorateBehavior, Optional.of(new PathRewrite(from, to)));
    }

    /** Adds a {@code wait} behavior to the recorded stub (only meaningful when this proxy records). */
    public ProxySpec addWaitBehavior() {
        return new ProxySpec(to, mode, predicateGenerators, true, injectHeaders, addDecorateBehavior, pathRewrite);
    }

    /** Post-processes the proxied response with the given decorator script before it is returned. */
    public ProxySpec decorateWith(String script) {
        return new ProxySpec(to, mode, predicateGenerators, addWaitBehavior, injectHeaders, Optional.of(script), pathRewrite);
    }

    /** Builds the immutable {@link Response.Proxy} this spec represents. */
    @Override
    public Response build() {
        return new Response.Proxy(new ProxyResponse(to, mode, predicateGenerators, addWaitBehavior, injectHeaders, addDecorateBehavior, pathRewrite));
    }
}
