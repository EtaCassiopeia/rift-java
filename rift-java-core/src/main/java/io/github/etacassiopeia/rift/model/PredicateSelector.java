package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A structured selector applied to the request body before predicate matching. Wire tag is
 * lowercase ({@code "xpath"} / {@code "jsonpath"}), flattened alongside {@link PredicateParameters}
 * and the {@link PredicateOperation} in the same JSON object.
 */
public sealed interface PredicateSelector {

    record XPath(String selector, Optional<Map<String, String>> namespaces) implements PredicateSelector {
        public XPath {
            namespaces = namespaces.map(ns -> Map.copyOf(new LinkedHashMap<>(ns)));
        }
    }

    record JsonPath(String selector) implements PredicateSelector {}

    /** Reads the selector out of a flattened predicate object, if one of the two keys is present. */
    static Optional<PredicateSelector> read(JsonObject obj) {
        if (obj.has("xpath")) {
            JsonObject xpath = JsonSupport.requireObject(obj.get("xpath"), "xpath");
            String selector = JsonSupport.requireString(xpath, "selector");
            Optional<Map<String, String>> ns = Optional.ofNullable(xpath.get("ns")).map(v -> {
                JsonObject nsObj = JsonSupport.requireObject(v, "xpath.ns");
                Map<String, String> out = new LinkedHashMap<>();
                for (var e : nsObj.fields().entrySet()) {
                    out.put(e.getKey(), JsonSupport.requireString(e.getValue(), "xpath.ns." + e.getKey()));
                }
                return out;
            });
            return Optional.of(new XPath(selector, ns));
        }
        if (obj.has("jsonpath")) {
            JsonObject jsonpath = JsonSupport.requireObject(obj.get("jsonpath"), "jsonpath");
            return Optional.of(new JsonPath(JsonSupport.requireString(jsonpath, "selector")));
        }
        return Optional.empty();
    }

    /**
     * Writes this selector's fields into the flattened predicate object builder.
     *
     * <p>Uses {@code instanceof} chains rather than pattern-matching {@code switch}: the latter is
     * still a preview feature on the Java 17 release level this module compiles against (finalized
     * only in Java 21), so this codebase avoids it throughout.
     */
    default void writeInto(JsonObject.Builder builder) {
        if (this instanceof XPath xpath) {
            JsonObject.Builder inner = JsonObject.builder().put("selector", new JsonString(xpath.selector()));
            xpath.namespaces().ifPresent(ns -> {
                JsonObject.Builder nsBuilder = JsonObject.builder();
                ns.forEach((k, v) -> nsBuilder.put(k, new JsonString(v)));
                inner.put("ns", nsBuilder.build());
            });
            builder.put("xpath", inner.build());
        } else if (this instanceof JsonPath jsonpath) {
            builder.put("jsonpath", JsonObject.builder().put("selector", new JsonString(jsonpath.selector())).build());
        }
    }
}
