package io.github.etacassiopeia.rift.dsl;

import io.github.etacassiopeia.rift.model.Predicate;
import io.github.etacassiopeia.rift.model.Response;
import io.github.etacassiopeia.rift.model.Stub;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A stub under construction: the predicates a request must match, and the responses to serve
 * (cycled in order) once it does.
 *
 * <p>Created by an opener such as {@link RiftDsl#onGet(String)} (seeded with a combined
 * method+path {@code equals} predicate) or {@link RiftDsl#onRequest()} (no seed), then extended
 * with further predicates and responses. Instances are immutable: every chain method returns a new
 * {@code StubSpec}. The terminal {@link #build()} produces the {@link Stub} model value.
 */
public final class StubSpec {

    private final List<Predicate> predicates;
    private final List<Response> responses;
    private final Optional<String> scenarioName;
    private final Optional<String> requiredScenarioState;
    private final Optional<String> newScenarioState;

    StubSpec(List<Predicate> predicates) {
        this(predicates, List.of(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private StubSpec(
            List<Predicate> predicates,
            List<Response> responses,
            Optional<String> scenarioName,
            Optional<String> requiredScenarioState,
            Optional<String> newScenarioState) {
        this.predicates = predicates;
        this.responses = responses;
        this.scenarioName = scenarioName;
        this.requiredScenarioState = requiredScenarioState;
        this.newScenarioState = newScenarioState;
    }

    /** Adds a predicate matching the request method against {@code matcher}. */
    public StubSpec withMethod(Matcher matcher) {
        return withPredicate(RiftDsl.method(matcher));
    }

    /** Adds a predicate matching the request path against {@code matcher}. */
    public StubSpec withPath(Matcher matcher) {
        return withPredicate(RiftDsl.path(matcher));
    }

    /** Adds a predicate matching the named request header against {@code matcher}. */
    public StubSpec withHeader(String name, Matcher matcher) {
        return withPredicate(RiftDsl.header(name, matcher));
    }

    /** Adds a predicate requiring the named request header to equal {@code value}. */
    public StubSpec withHeader(String name, String value) {
        return withHeader(name, RiftDsl.equals(value));
    }

    /** Adds a predicate matching the named query-string parameter against {@code matcher}. */
    public StubSpec withQuery(String name, Matcher matcher) {
        return withPredicate(RiftDsl.query(name, matcher));
    }

    /** Adds a predicate requiring the named query-string parameter to equal {@code value}. */
    public StubSpec withQuery(String name, String value) {
        return withQuery(name, RiftDsl.equals(value));
    }

    /** Adds a predicate matching the request body against {@code matcher}. */
    public StubSpec withBody(Matcher matcher) {
        return withPredicate(RiftDsl.body(matcher));
    }

    /** Adds an arbitrary predicate — for combinators ({@code and}/{@code or}/{@code not}) and structured selectors. */
    public StubSpec withPredicate(PredicateSpec predicate) {
        List<Predicate> next = Stream.concat(predicates.stream(), Stream.of(predicate.build())).toList();
        return new StubSpec(next, responses, scenarioName, requiredScenarioState, newScenarioState);
    }

    /** Adds a response. Repeatable: multiple calls model response cycling (served in call order). */
    public StubSpec willReturn(ResponseSpec response) {
        return new StubSpec(predicates, appendResponse(response.build()), scenarioName, requiredScenarioState, newScenarioState);
    }

    /** Adds a proxy response. Repeatable: multiple calls model response cycling (served in call order). */
    public StubSpec willReturn(ProxySpec proxy) {
        return new StubSpec(predicates, appendResponse(proxy.build()), scenarioName, requiredScenarioState, newScenarioState);
    }

    private List<Response> appendResponse(Response response) {
        return Stream.concat(responses.stream(), Stream.of(response)).toList();
    }

    /**
     * Tags this stub with a bare scenario name, with no state-transition gate — the label a fixture
     * uses purely for documentation/grouping, as opposed to the full FSM transition {@link
     * RiftDsl#scenario(String)} builds.
     */
    public StubSpec scenario(String name) {
        return new StubSpec(predicates, responses, Optional.of(name), requiredScenarioState, newScenarioState);
    }

    /** Attaches a full scenario-FSM transition: name, required (from) state, and new (to) state. Used by {@link ScenarioSpec}. */
    StubSpec withScenarioTransition(String scenarioName, Optional<String> requiredState, String newState) {
        return new StubSpec(predicates, responses, Optional.of(scenarioName), requiredState, Optional.of(newState));
    }

    /** Builds the immutable {@link Stub} this spec represents. */
    public Stub build() {
        return new Stub(
                scenarioName, requiredScenarioState, newScenarioState, Optional.empty(), Optional.empty(),
                Optional.empty(), predicates, responses, Optional.empty(), Optional.empty());
    }
}
