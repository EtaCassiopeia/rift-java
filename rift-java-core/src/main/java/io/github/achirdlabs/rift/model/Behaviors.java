package io.github.achirdlabs.rift.model;

import io.github.achirdlabs.rift.json.JsonArray;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * A response's behaviors: an ordered set of {@link Behavior}s. Two wire shapes are accepted — the
 * {@code _behaviors} JSON object every fixture uses ({@code {"wait": 100, "decorate": "..."}}) and
 * the {@code behaviors} array-of-single-key-objects the engine emits in {@code GET /imposters}
 * output. Serialization always uses the object form; that collapses repeated keys (e.g. two
 * {@code copy} entries), which the object form cannot represent — a limitation only reachable by
 * engine output that repeats a behavior key, which the fixtures never do.
 */
public record Behaviors(List<Behavior> entries) {

    public Behaviors {
        entries = List.copyOf(entries);
    }

    public static final Behaviors EMPTY = new Behaviors(List.of());

    static Behaviors read(JsonObject obj) {
        return new Behaviors(obj.fields().entrySet().stream()
                .map(e -> Behavior.read(e.getKey(), e.getValue()))
                .toList());
    }

    /** Reads the array-of-single-key-objects form: {@code [{"wait":100},{"decorate":"..."}]}. */
    static Behaviors readArray(JsonArray arr) {
        List<Behavior> entries = new ArrayList<>();
        for (JsonValue el : arr.items()) {
            JsonObject entry = JsonSupport.requireObject(el, "behaviors[]");
            if (entry.fields().size() != 1) {
                throw new WireFormatException("behaviors[]: each entry must have exactly one key");
            }
            var e = entry.fields().entrySet().iterator().next();
            entries.add(Behavior.read(e.getKey(), e.getValue()));
        }
        return new Behaviors(entries);
    }

    JsonObject toJsonValue() {
        JsonObject.Builder builder = JsonObject.builder();
        entries.forEach(b -> builder.put(b.key(), b.value()));
        return builder.build();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
