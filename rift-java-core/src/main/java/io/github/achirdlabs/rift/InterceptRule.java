package io.github.achirdlabs.rift;

import io.github.achirdlabs.rift.json.JsonArray;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonValue;
import io.github.achirdlabs.rift.model.Predicate;

import java.util.List;

/**
 * A single intercept rule: the host it matches (may be {@code null} for a catch-all) and what it
 * does ({@link #kind()}). {@link #raw()} is the rule's full wire JSON (the engine's {@code {host,
 * predicates, action}} shape), kept alongside the typed fields so nothing about a rule fetched via
 * {@link Intercept#rules()} is lost even though the typed surface only projects {@code host}/{@code
 * kind} out of it.
 */
public record InterceptRule(String host, RuleKind kind, JsonValue raw) {

    /** The rule's predicates (empty for a host-only or catch-all rule), parsed from {@link #raw()}. */
    public List<Predicate> predicates() {
        if (raw instanceof JsonObject obj && obj.get("predicates") instanceof JsonArray arr) {
            return arr.items().stream().map(v -> Predicate.fromJson(v.toJson())).toList();
        }
        return List.of();
    }
}
