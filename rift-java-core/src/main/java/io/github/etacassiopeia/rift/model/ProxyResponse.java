package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonBool;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A proxy response: forwards the matched request to an upstream and (optionally) records the
 * result as a new stub. {@code mode}, {@code predicateGenerators}, {@code addWaitBehavior} and
 * {@code injectHeaders} are always present on the wire, even at their default/empty value — this
 * mirrors the engine's own serialization, which has no {@code skip_serializing_if} on those
 * fields (unlike {@code addDecorateBehavior} and {@code pathRewrite}, which are omitted when
 * absent).
 *
 * <p>{@code extra} carries any wire keys not modeled above, so unknown/future engine fields survive
 * a parse → serialize round-trip instead of being dropped (re-emitted after the modeled keys, in
 * insertion order). A modeled key in {@code extra} is rejected at construction.
 */
public record ProxyResponse(
        String to,
        String mode,
        List<JsonValue> predicateGenerators,
        boolean addWaitBehavior,
        Map<String, String> injectHeaders,
        Optional<String> addDecorateBehavior,
        Optional<PathRewrite> pathRewrite,
        Map<String, JsonValue> extra) {

    private static final Set<String> MODELED_KEYS = Set.of(
            "to", "mode", "predicateGenerators", "addWaitBehavior", "injectHeaders",
            "addDecorateBehavior", "pathRewrite");

    public ProxyResponse {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(mode, "mode");
        predicateGenerators = List.copyOf(predicateGenerators);
        injectHeaders = JsonSupport.orderedCopy(injectHeaders);
        Objects.requireNonNull(addDecorateBehavior, "addDecorateBehavior");
        Objects.requireNonNull(pathRewrite, "pathRewrite");
        Objects.requireNonNull(extra, "extra");
        JsonSupport.rejectModeledExtraKeys(extra, MODELED_KEYS, "proxy response");
        extra = JsonSupport.orderedCopy(extra);
    }

    public ProxyResponse(String to) {
        this(to, "", List.of(), false, Map.of(), Optional.empty(), Optional.empty(), Map.of());
    }

    public ProxyResponse(String to, String mode, List<JsonValue> predicateGenerators, boolean addWaitBehavior,
            Map<String, String> injectHeaders, Optional<String> addDecorateBehavior, Optional<PathRewrite> pathRewrite) {
        this(to, mode, predicateGenerators, addWaitBehavior, injectHeaders, addDecorateBehavior, pathRewrite, Map.of());
    }

    /** Returns a copy with {@code key}/{@code value} added to {@code extra}; rejects a modeled key. */
    public ProxyResponse withExtra(String extraKey, JsonValue value) {
        Objects.requireNonNull(extraKey, "extraKey");
        Objects.requireNonNull(value, "value");
        Map<String, JsonValue> next = new LinkedHashMap<>(extra);
        next.put(extraKey, value);
        return new ProxyResponse(to, mode, predicateGenerators, addWaitBehavior, injectHeaders,
                addDecorateBehavior, pathRewrite, next);
    }

    static ProxyResponse read(JsonObject obj) {
        Map<String, String> injectHeaders = new LinkedHashMap<>();
        JsonValue injectHeadersValue = obj.get("injectHeaders");
        if (injectHeadersValue != null) {
            for (var e : JsonSupport.requireObject(injectHeadersValue, "injectHeaders").fields().entrySet()) {
                injectHeaders.put(e.getKey(), JsonSupport.requireString(e.getValue(), "'injectHeaders." + e.getKey() + "'"));
            }
        }
        return new ProxyResponse(
                JsonSupport.requireString(obj, "to"),
                JsonSupport.optString(obj, "mode").orElse(""),
                JsonSupport.optArray(obj, "predicateGenerators", v -> v),
                JsonSupport.optBool(obj, "addWaitBehavior", false),
                injectHeaders,
                JsonSupport.optString(obj, "addDecorateBehavior"),
                Optional.ofNullable(obj.get("pathRewrite")).map(v -> PathRewrite.read(JsonSupport.requireObject(v, "pathRewrite"))),
                JsonSupport.extraFields(obj, MODELED_KEYS));
    }

    JsonObject toJsonValue() {
        JsonObject.Builder builder = JsonObject.builder();
        builder.put("to", new JsonString(to));
        builder.put("mode", new JsonString(mode));
        builder.put("predicateGenerators", new JsonArray(predicateGenerators));
        builder.put("addWaitBehavior", JsonBool.of(addWaitBehavior));
        JsonObject.Builder injectHeadersBuilder = JsonObject.builder();
        injectHeaders.forEach((k, v) -> injectHeadersBuilder.put(k, new JsonString(v)));
        builder.put("injectHeaders", injectHeadersBuilder.build());
        addDecorateBehavior.ifPresent(v -> builder.put("addDecorateBehavior", new JsonString(v)));
        pathRewrite.ifPresent(v -> builder.put("pathRewrite", v.toJsonValue()));
        extra.forEach(builder::put);
        return builder.build();
    }
}
