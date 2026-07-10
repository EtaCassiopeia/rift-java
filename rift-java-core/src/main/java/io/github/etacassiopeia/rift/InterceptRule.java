package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.json.JsonValue;

/**
 * A single intercept rule: the host it matches and what it does ({@link #kind()}). {@link #raw()}
 * is the rule's full wire JSON (the engine's {@code {host, predicates, action}} shape), kept
 * alongside the typed fields so nothing about a rule fetched via {@link Intercept#rules()} is
 * lost even though the typed surface only projects {@code host}/{@code kind} out of it.
 */
public record InterceptRule(String host, RuleKind kind, JsonValue raw) {
}
