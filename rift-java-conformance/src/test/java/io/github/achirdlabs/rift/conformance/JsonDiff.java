package io.github.achirdlabs.rift.conformance;

import io.github.achirdlabs.rift.json.JsonArray;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonValue;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A best-effort locator for the first place two JSON trees diverge, used to turn a failed
 * {@link JsonValue#semanticEquals} check into a path-precise message (e.g.
 * {@code stubs[0].responses[0].is.statusCode: 200 != 503}) instead of dumping two whole documents.
 *
 * <p>Object key order is ignored (matching the wire model). This is a diagnostic aid, not the gate:
 * it is only consulted after {@code semanticEquals} has already returned {@code false}, so any path
 * it reports reflects a genuine divergence.
 */
final class JsonDiff {

    private JsonDiff() {}

    /** The first divergence between {@code expected} and {@code actual}, or empty if raw-equal. */
    static Optional<String> firstDifference(JsonValue expected, JsonValue actual) {
        return Optional.ofNullable(diff("", expected, actual));
    }

    private static String diff(String path, JsonValue expected, JsonValue actual) {
        if (expected instanceof JsonObject eo && actual instanceof JsonObject ao) {
            Set<String> keys = new LinkedHashSet<>(eo.fields().keySet());
            keys.addAll(ao.fields().keySet());
            for (String key : keys) {
                String child = path.isEmpty() ? key : path + "." + key;
                if (!eo.has(key)) {
                    return child + ": missing on expected side";
                }
                if (!ao.has(key)) {
                    return child + ": missing on actual side";
                }
                String nested = diff(child, eo.get(key), ao.get(key));
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        if (expected instanceof JsonArray ea && actual instanceof JsonArray aa) {
            if (ea.items().size() != aa.items().size()) {
                return path + ": array size " + ea.items().size() + " != " + aa.items().size();
            }
            for (int i = 0; i < ea.items().size(); i++) {
                String nested = diff(path + "[" + i + "]", ea.items().get(i), aa.items().get(i));
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        if (!expected.toJson().equals(actual.toJson())) {
            return (path.isEmpty() ? "<root>" : path) + ": " + expected.toJson() + " != " + actual.toJson();
        }
        return null;
    }
}
