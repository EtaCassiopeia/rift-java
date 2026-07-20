package io.github.achirdlabs.rift.model;

import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonString;

import java.util.Optional;

/**
 * Script configuration for response generation. Exactly one of {@code code}, {@code file}, or
 * {@code ref} is valid; this is enforced by the engine's config-time resolution pass, not by the
 * wire codec, so a config using only {@code code} (the legacy shape) keeps parsing unchanged.
 */
public record RiftScriptConfig(Optional<String> engine, Optional<String> code, Optional<String> file, Optional<String> ref) {

    public RiftScriptConfig {
        java.util.Objects.requireNonNull(engine, "engine");
        java.util.Objects.requireNonNull(code, "code");
        java.util.Objects.requireNonNull(file, "file");
        java.util.Objects.requireNonNull(ref, "ref");
    }

    public static final RiftScriptConfig EMPTY =
            new RiftScriptConfig(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    /** How many of code/file/ref are set; the engine treats exactly one as valid. */
    public int sourceCount() {
        return (code.isPresent() ? 1 : 0) + (file.isPresent() ? 1 : 0) + (ref.isPresent() ? 1 : 0);
    }

    static RiftScriptConfig read(JsonObject obj) {
        return new RiftScriptConfig(
                JsonSupport.optString(obj, "engine"),
                JsonSupport.optString(obj, "code"),
                JsonSupport.optString(obj, "file"),
                JsonSupport.optString(obj, "ref"));
    }

    JsonObject toJsonValue() {
        JsonObject.Builder builder = JsonObject.builder();
        engine.ifPresent(v -> builder.put("engine", new JsonString(v)));
        code.ifPresent(v -> builder.put("code", new JsonString(v)));
        file.ifPresent(v -> builder.put("file", new JsonString(v)));
        ref.ifPresent(v -> builder.put("ref", new JsonString(v)));
        return builder.build();
    }
}
