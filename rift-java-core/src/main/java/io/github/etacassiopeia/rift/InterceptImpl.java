package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.dsl.IsSpec;
import io.github.etacassiopeia.rift.error.CommunicationError;
import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonNumber;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.IsResponse;
import io.github.etacassiopeia.rift.model.Predicate;
import io.github.etacassiopeia.rift.model.Response;
import io.github.etacassiopeia.rift.transport.RiftTransport;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link Intercept} over a {@link RiftTransport}: every method is a thin JSON-shape translation
 * layer around {@code transport.intercept*}, matching the engine's {@code InterceptRule}/{@code
 * InterceptAction} wire model (host + predicates + one of {@code {"serve":...}}/{@code
 * {"forward":{"port":...}}}) — see {@code intercept_rules.rs} in the rift engine.
 */
final class InterceptImpl implements Intercept {

    private final RiftTransport transport;
    private final InetSocketAddress address;
    private final URI uri;

    private volatile InterceptTrust trust;

    InterceptImpl(RiftTransport transport, JsonValue startResponse) {
        this.transport = transport;
        if (!(startResponse instanceof JsonObject obj)
                || !(obj.get("interceptPort") instanceof JsonNumber port)
                || !(obj.get("interceptUrl") instanceof JsonString url)) {
            throw new CommunicationError(
                    "rift engine's intercept start response is missing 'interceptPort'/'interceptUrl': "
                            + startResponse.toJson());
        }
        this.uri = URI.create(url.value());
        this.address = new InetSocketAddress(uri.getHost(), port.asInt());
    }

    @Override
    public InetSocketAddress address() {
        return address;
    }

    @Override
    public URI uri() {
        return uri;
    }

    @Override
    public ProxySelector proxySelector() {
        return ProxySelector.of(address);
    }

    @Override
    public InterceptRule serve(String host, IsSpec response) {
        return addServeRule(host, List.of(), response, RuleKind.SERVE);
    }

    @Override
    public InterceptRule forward(String host, String hostPort) {
        return addForwardRule(host, List.of(), parsePort(hostPort), RuleKind.FORWARD);
    }

    @Override
    public InterceptRule redirectTo(String host, Imposter imposter) {
        // REDIRECT is an SDK-level label only: the wire action is identical to forward()'s (see
        // RuleKind), so this rule is indistinguishable from a plain forward() once round-tripped.
        return addForwardRule(host, List.of(), imposter.port(), RuleKind.REDIRECT);
    }

    @Override
    public InterceptRuleBuilder rule() {
        return new InterceptRuleBuilder(this);
    }

    // Shared by the host-only methods above and by InterceptRuleBuilder (predicate-scoped, host-optional).
    InterceptRule addServeRule(String host, List<Predicate> predicates, IsSpec response, RuleKind kind) {
        JsonObject action = JsonObject.builder().put("serve", toServeStub(response)).build();
        JsonObject rule = ruleJson(host, predicates, action);
        transport.interceptAddRules(rule);
        return new InterceptRule(host, kind, rule);
    }

    InterceptRule addForwardRule(String host, List<Predicate> predicates, int port, RuleKind kind) {
        JsonObject action = JsonObject.builder()
                .put("forward", JsonObject.builder().put("port", JsonNumber.of(port)).build())
                .build();
        JsonObject rule = ruleJson(host, predicates, action);
        transport.interceptAddRules(rule);
        return new InterceptRule(host, kind, rule);
    }

    /**
     * The engine's {@code InterceptRule} wire shape: an <em>optional</em> {@code host} (absent = match
     * any intercepted host), the {@code predicates} matched like stub predicates (omitted when empty),
     * and the action — see {@code intercept_rules.rs}.
     */
    private static JsonObject ruleJson(String host, List<Predicate> predicates, JsonObject action) {
        JsonObject.Builder builder = JsonObject.builder();
        if (host != null) {
            builder.put("host", new JsonString(host));
        }
        if (!predicates.isEmpty()) {
            builder.put("predicates", new JsonArray(
                    predicates.stream().map(p -> (JsonValue) JsonValue.parse(p.toJson())).toList()));
        }
        return builder.put("action", action).build();
    }

    /**
     * Extracts the trailing port number from a {@code host:port} (or bare-port) string. The
     * engine's {@code forward} action only ever targets a numeric localhost imposter port (see
     * {@code ForwardTarget { port: u16 }} in {@code intercept_rules.rs}) — any host component here
     * is for this method's own convenience only and is never sent over the wire.
     */
    static int parsePort(String hostPort) {
        int colon = hostPort.lastIndexOf(':');
        String portPart = colon >= 0 ? hostPort.substring(colon + 1) : hostPort;
        try {
            return Integer.parseInt(portPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a valid host:port (or bare port): " + hostPort, e);
        }
    }

    @Override
    public List<InterceptRule> rules() {
        JsonValue listed = transport.interceptListRules();
        if (!(listed instanceof JsonArray array)) {
            throw new CommunicationError(
                    "rift engine's intercept rule list response is not a JSON array: " + listed.toJson());
        }
        List<InterceptRule> out = new ArrayList<>();
        for (JsonValue item : array.items()) {
            out.add(readRule(item));
        }
        return List.copyOf(out);
    }

    private static InterceptRule readRule(JsonValue item) {
        if (!(item instanceof JsonObject obj)) {
            throw new CommunicationError("intercept rule is not a JSON object: " + item.toJson());
        }
        String host = obj.get("host") instanceof JsonString h ? h.value() : "";
        return new InterceptRule(host, ruleKind(obj), obj);
    }

    private static RuleKind ruleKind(JsonObject obj) {
        if (obj.get("action") instanceof JsonObject action) {
            if (action.has("serve")) {
                return RuleKind.SERVE;
            }
            if (action.has("forward")) {
                return RuleKind.FORWARD;
            }
        }
        throw new CommunicationError("intercept rule has an unrecognized 'action': " + obj.toJson());
    }

    @Override
    public void clearRules() {
        transport.interceptClearRules();
    }

    @Override
    public InterceptTrust trust() {
        InterceptTrust t = trust;
        if (t == null) {
            synchronized (this) {
                t = trust;
                if (t == null) {
                    t = new InterceptTrustImpl(transport.interceptCaPem());
                    trust = t;
                }
            }
        }
        return t;
    }

    @Override
    public void close() {
        // The intercept listener itself has no per-instance stop over this SPI — it is torn down
        // only when the owning engine (and thus its Rift/RiftTransport) is closed. Clearing rules
        // is the best-effort cleanup available here.
        clearRules();
    }

    /**
     * Projects an {@link IsSpec} down to the engine's flat {@code ServeStub} shape: a numeric
     * {@code statusCode}, single-valued {@code headers}, and a plain-text {@code body} (see
     * {@code ServeStub} in {@code intercept_rules.rs}) — narrower than the full {@code is} response
     * shape a stub uses (multi-value headers, a structured JSON body, behaviors, faults). Anything
     * beyond status/headers/body on the given response is not carried over: the intercept Serve
     * action has no behaviors/faults concept.
     */
    private static JsonObject toServeStub(IsSpec response) {
        if (!(response.build() instanceof Response.Is is)) {
            throw new IllegalStateException("unreachable: IsSpec.build() always returns Response.Is");
        }
        IsResponse ir = is.is();
        JsonObject.Builder builder = JsonObject.builder();
        builder.put("statusCode", JsonNumber.of(statusAsInt(ir.statusCode())));
        if (!ir.headers().isEmpty()) {
            JsonObject.Builder headers = JsonObject.builder();
            ir.headers().forEach((name, values) -> {
                if (!values.isEmpty()) {
                    headers.put(name, new JsonString(values.get(0)));
                }
            });
            builder.put("headers", headers.build());
        }
        ir.body().ifPresent(body -> builder.put("body", new JsonString(bodyAsText(body))));
        return builder.build();
    }

    private static int statusAsInt(String statusCode) {
        try {
            return Integer.parseInt(statusCode);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "intercept serve() requires a numeric status code, got: " + statusCode, e);
        }
    }

    private static String bodyAsText(JsonValue body) {
        return body instanceof JsonString s ? s.value() : body.toJson();
    }
}
