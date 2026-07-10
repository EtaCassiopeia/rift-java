package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonValue;

import java.util.List;

/** The admin-API collection shape: {@code {"imposters": [ ... ]}}. */
public record ImposterDefinitions(List<ImposterDefinition> imposters) {

    public ImposterDefinitions {
        imposters = List.copyOf(imposters);
    }

    /** Parses {@code {"imposters": [...]}}. Throws a typed codec error on malformed input. */
    public static ImposterDefinitions fromJson(String json) {
        JsonObject obj = JsonSupport.requireObject(JsonValue.parse(json), "imposters document");
        JsonValue imposters = obj.get("imposters");
        if (imposters == null) {
            throw new WireFormatException("expected an 'imposters' array at the document root");
        }
        List<ImposterDefinition> parsed = JsonSupport.requireArray(imposters, "imposters")
                .items().stream()
                .map(v -> ImposterDefinition.read(JsonSupport.requireObject(v, "imposters[]")))
                .toList();
        return new ImposterDefinitions(parsed);
    }

    public String toJson() {
        return toJsonValue().toJson();
    }

    private JsonObject toJsonValue() {
        return JsonObject.builder()
                .put("imposters", new JsonArray(imposters.stream().map(imp -> (JsonValue) imp.toJsonValue()).toList()))
                .build();
    }
}
