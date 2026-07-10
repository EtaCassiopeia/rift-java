package io.github.etacassiopeia.rift.dsl;

import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.PathRewrite;
import io.github.etacassiopeia.rift.model.ProxyResponse;
import io.github.etacassiopeia.rift.model.Response;

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
public final class ProxySpec {

    private final String to;
    private final String mode;
    private final List<JsonValue> predicateGenerators;
    private final Map<String, String> injectHeaders;
    private final Optional<PathRewrite> pathRewrite;

    private ProxySpec(
            String to,
            String mode,
            List<JsonValue> predicateGenerators,
            Map<String, String> injectHeaders,
            Optional<PathRewrite> pathRewrite) {
        this.to = to;
        this.mode = mode;
        this.predicateGenerators = predicateGenerators;
        this.injectHeaders = injectHeaders;
        this.pathRewrite = pathRewrite;
    }

    /** A fresh proxy targeting {@code url}, with the engine's default proxy mode. */
    static ProxySpec to(String url) {
        return new ProxySpec(url, "", List.of(), Map.of(), Optional.empty());
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
        return new ProxySpec(to, newMode, predicateGenerators, injectHeaders, pathRewrite);
    }

    /** Adds a predicate generator, controlling which parts of a proxied request become a recorded predicate. */
    public ProxySpec withPredicateGenerator(JsonValue generator) {
        List<JsonValue> next = Stream.concat(predicateGenerators.stream(), Stream.of(generator)).toList();
        return new ProxySpec(to, mode, next, injectHeaders, pathRewrite);
    }

    /** Adds a predicate generator, parsing {@code jsonText} as its JSON definition. */
    public ProxySpec withPredicateGenerator(String jsonText) {
        return withPredicateGenerator(RiftDsl.json(jsonText));
    }

    /** Injects the given header into the proxied (upstream) request. Repeatable. */
    public ProxySpec injectHeader(String name, String value) {
        Map<String, String> next = new LinkedHashMap<>(injectHeaders);
        next.put(name, value);
        return new ProxySpec(to, mode, predicateGenerators, next, pathRewrite);
    }

    /** Rewrites the {@code from} substring of the proxied request's path to {@code to}. */
    public ProxySpec rewritePath(String from, String to) {
        return new ProxySpec(this.to, mode, predicateGenerators, injectHeaders, Optional.of(new PathRewrite(from, to)));
    }

    /** Builds the immutable {@link Response.Proxy} this spec represents. */
    public Response build() {
        return new Response.Proxy(new ProxyResponse(to, mode, predicateGenerators, false, injectHeaders, Optional.empty(), pathRewrite));
    }
}
