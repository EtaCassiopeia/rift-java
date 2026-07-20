package io.github.achirdlabs.rift;

import io.github.achirdlabs.rift.error.CommunicationError;
import io.github.achirdlabs.rift.json.JsonArray;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonString;
import io.github.achirdlabs.rift.json.JsonValue;

import java.util.LinkedHashSet;
import java.util.Set;

/** The running engine's {@code GET /config} response: version, commit, and supported features. */
public record EngineInfo(String version, String commit, Set<String> features) {

    public EngineInfo {
        features = Set.copyOf(features);
    }

    /**
     * Reads an {@code EngineInfo}. A missing/mistyped {@code version} on a {@code GET /config} response
     * is a {@link CommunicationError} (aligning with the connect-time version preflight, which reads the
     * same endpoint); {@code commit} and {@code features} are advisory and default when absent.
     */
    public static EngineInfo read(JsonValue value) {
        if (!(value instanceof JsonObject obj) || !(obj.get("version") instanceof JsonString versionStr)) {
            throw new CommunicationError("rift admin API GET /config response is missing a 'version' field");
        }
        String version = versionStr.value();
        String commit = stringOrEmpty(obj, "commit");
        Set<String> features = new LinkedHashSet<>();
        if (obj.get("features") instanceof JsonArray arr) {
            for (JsonValue v : arr.items()) {
                if (v instanceof JsonString s) {
                    features.add(s.value());
                }
            }
        }
        return new EngineInfo(version, commit, features);
    }

    private static String stringOrEmpty(JsonObject obj, String key) {
        return obj.get(key) instanceof JsonString s ? s.value() : "";
    }
}
