package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;

import java.util.List;
import java.util.Optional;

/**
 * A stub: predicates to match a request, and the responses to serve (cycled) when it matches, plus
 * scenario-FSM and correlated-isolation ("space") extensions.
 */
public record Stub(
        Optional<String> scenarioName,
        Optional<String> requiredScenarioState,
        Optional<String> newScenarioState,
        Optional<String> space,
        Optional<String> id,
        Optional<String> routePattern,
        List<Predicate> predicates,
        List<Response> responses,
        Optional<String> recordedFrom,
        Optional<JsonValue> verify) {

    public Stub {
        java.util.Objects.requireNonNull(scenarioName, "scenarioName");
        java.util.Objects.requireNonNull(requiredScenarioState, "requiredScenarioState");
        java.util.Objects.requireNonNull(newScenarioState, "newScenarioState");
        java.util.Objects.requireNonNull(space, "space");
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(routePattern, "routePattern");
        predicates = List.copyOf(predicates);
        responses = List.copyOf(responses);
        java.util.Objects.requireNonNull(recordedFrom, "recordedFrom");
        java.util.Objects.requireNonNull(verify, "verify");
    }

    public Stub(List<Predicate> predicates, List<Response> responses) {
        this(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), predicates, responses, Optional.empty(), Optional.empty());
    }

    /** Parses a single stub JSON object. Throws a typed codec error on malformed input. */
    public static Stub fromJson(String json) {
        return read(JsonSupport.requireObject(JsonValue.parse(json), "stub"));
    }

    public String toJson() {
        return toJsonValue().toJson();
    }

    static Stub read(JsonObject obj) {
        return new Stub(
                JsonSupport.optString(obj, "scenarioName"),
                JsonSupport.optString(obj, "requiredScenarioState"),
                JsonSupport.optString(obj, "newScenarioState"),
                JsonSupport.optString(obj, "space"),
                JsonSupport.optString(obj, "id"),
                JsonSupport.optString(obj, "routePattern"),
                JsonSupport.optArray(obj, "predicates", v -> Predicate.read(JsonSupport.requireObject(v, "predicates[]"))),
                JsonSupport.optArray(obj, "responses", v -> Response.read(JsonSupport.requireObject(v, "responses[]"))),
                JsonSupport.optString(obj, "recordedFrom"),
                Optional.ofNullable(obj.get("_verify")));
    }

    JsonObject toJsonValue() {
        JsonObject.Builder builder = JsonObject.builder();
        scenarioName.ifPresent(v -> builder.put("scenarioName", new JsonString(v)));
        requiredScenarioState.ifPresent(v -> builder.put("requiredScenarioState", new JsonString(v)));
        newScenarioState.ifPresent(v -> builder.put("newScenarioState", new JsonString(v)));
        space.ifPresent(v -> builder.put("space", new JsonString(v)));
        id.ifPresent(v -> builder.put("id", new JsonString(v)));
        routePattern.ifPresent(v -> builder.put("routePattern", new JsonString(v)));
        builder.put("predicates", new JsonArray(predicates.stream().map(Predicate::toJsonValue).toList()));
        builder.put("responses", new JsonArray(responses.stream().map(r -> (JsonValue) r.toJsonValue()).toList()));
        recordedFrom.ifPresent(v -> builder.put("recordedFrom", new JsonString(v)));
        verify.ifPresent(v -> builder.put("_verify", v));
        return builder.build();
    }
}
