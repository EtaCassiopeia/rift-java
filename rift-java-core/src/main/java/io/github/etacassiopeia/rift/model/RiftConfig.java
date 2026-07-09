package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonObject;

import java.util.Map;
import java.util.Optional;

/**
 * Top-level {@code _rift} configuration block for an imposter: flow state, metrics, proxy, script
 * engine defaults, and a named script registry (issue #356 in the engine — a response script can
 * reference a registry entry via {@code {"ref": "name"}} instead of inlining code).
 */
public record RiftConfig(
        Optional<RiftFlowStateConfig> flowState,
        Optional<RiftMetricsConfig> metrics,
        Optional<RiftProxyConfig> proxy,
        Optional<RiftScriptEngineConfig> scriptEngine,
        Map<String, RiftScriptConfig> scripts) {

    public static final RiftConfig EMPTY =
            new RiftConfig(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Map.of());

    public RiftConfig {
        java.util.Objects.requireNonNull(flowState, "flowState");
        java.util.Objects.requireNonNull(metrics, "metrics");
        java.util.Objects.requireNonNull(proxy, "proxy");
        java.util.Objects.requireNonNull(scriptEngine, "scriptEngine");
        scripts = JsonSupport.orderedCopy(scripts);
    }

    static RiftConfig read(JsonObject obj) {
        Map<String, RiftScriptConfig> scripts = new java.util.LinkedHashMap<>();
        if (obj.get("scripts") != null) {
            for (var e : JsonSupport.requireObject(obj.get("scripts"), "scripts").fields().entrySet()) {
                scripts.put(e.getKey(), RiftScriptConfig.read(JsonSupport.requireObject(e.getValue(), "scripts[]")));
            }
        }
        return new RiftConfig(
                Optional.ofNullable(obj.get("flowState")).map(v -> RiftFlowStateConfig.read(JsonSupport.requireObject(v, "flowState"))),
                Optional.ofNullable(obj.get("metrics")).map(v -> RiftMetricsConfig.read(JsonSupport.requireObject(v, "metrics"))),
                Optional.ofNullable(obj.get("proxy")).map(v -> RiftProxyConfig.read(JsonSupport.requireObject(v, "proxy"))),
                Optional.ofNullable(obj.get("scriptEngine")).map(v -> RiftScriptEngineConfig.read(JsonSupport.requireObject(v, "scriptEngine"))),
                scripts);
    }

    JsonObject toJsonValue() {
        JsonObject.Builder builder = JsonObject.builder();
        flowState.ifPresent(v -> builder.put("flowState", v.toJsonValue()));
        metrics.ifPresent(v -> builder.put("metrics", v.toJsonValue()));
        proxy.ifPresent(v -> builder.put("proxy", v.toJsonValue()));
        scriptEngine.ifPresent(v -> builder.put("scriptEngine", v.toJsonValue()));
        if (!scripts.isEmpty()) {
            JsonObject.Builder scriptsBuilder = JsonObject.builder();
            scripts.forEach((k, v) -> scriptsBuilder.put(k, v.toJsonValue()));
            builder.put("scripts", scriptsBuilder.build());
        }
        return builder.build();
    }

    public boolean isEmpty() {
        return flowState.isEmpty() && metrics.isEmpty() && proxy.isEmpty() && scriptEngine.isEmpty() && scripts.isEmpty();
    }
}
