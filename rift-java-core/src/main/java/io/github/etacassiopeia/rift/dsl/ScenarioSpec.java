package io.github.etacassiopeia.rift.dsl;

import io.github.etacassiopeia.rift.model.Stub;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A scenario finite-state machine under construction, created by {@link RiftDsl#scenario(String)}.
 *
 * <p>A scenario has a name, an optional {@link #startingAt(String) starting state}, and any number
 * of transitions added via {@code .when(fromState, stub).respond(response).goTo(toState)}. Each
 * transition produces one {@link Stub} carrying the scenario name plus the from/to state pair
 * ({@code requiredScenarioState}/{@code newScenarioState}); the from-state named by {@link
 * #startingAt(String)} is written as <em>no</em> {@code requiredScenarioState}, matching the
 * engine's convention that a scenario with no recorded state is in its starting state.
 *
 * <p>Instances are immutable. The {@code requiredScenarioState} of each transition is resolved
 * lazily in {@link #stubs()} against the final starting state, so {@link #startingAt(String)} and
 * the transitions may be declared in any order without silently mis-guarding the starting transition.
 */
public final class ScenarioSpec {

    private final String name;
    private final Optional<String> startState;
    private final List<PendingTransition> transitions;

    ScenarioSpec(String name) {
        this(name, Optional.empty(), List.of());
    }

    private ScenarioSpec(String name, Optional<String> startState, List<PendingTransition> transitions) {
        this.name = name;
        this.startState = startState;
        this.transitions = transitions;
    }

    /** Names the scenario's starting state, so transitions out of it omit {@code requiredScenarioState}. */
    public ScenarioSpec startingAt(String state) {
        return new ScenarioSpec(name, Optional.of(state), transitions);
    }

    /** Begins a transition out of {@code fromState}, gated by the given seed stub's predicates. */
    public Transition when(String fromState, StubSpec stub) {
        return new Transition(fromState, stub);
    }

    /**
     * All transitions added so far, as built {@link Stub} values, in the order they were added. The
     * {@code requiredScenarioState} guard is computed here against the final starting state.
     *
     * @throws IllegalStateException if {@link #startingAt(String)} was never called — without a
     *     declared starting state, the first transition's {@code requiredScenarioState} guard would
     *     be unsatisfiable (the engine's scenario state is unset until some transition sets it).
     */
    public List<Stub> stubs() {
        if (startState.isEmpty()) {
            throw new IllegalStateException(
                    "scenario \"" + name + "\" has no starting state — call startingAt(...) before stubs()");
        }
        return transitions.stream().map(this::buildStub).toList();
    }

    private Stub buildStub(PendingTransition t) {
        Optional<String> required = startState.get().equals(t.fromState())
                ? Optional.empty() : Optional.of(t.fromState());
        return t.stub().withScenarioTransition(name, required, t.toState()).build();
    }

    private ScenarioSpec withTransition(PendingTransition t) {
        return new ScenarioSpec(name, startState, Stream.concat(transitions.stream(), Stream.of(t)).toList());
    }

    private record PendingTransition(String fromState, StubSpec stub, String toState) {}

    /** The second step of a scenario transition: attaches the response served when the gate matches. */
    public final class Transition {

        private final String fromState;
        private final StubSpec stub;

        private Transition(String fromState, StubSpec stub) {
            this.fromState = fromState;
            this.stub = stub;
        }

        /** Attaches the response this transition's stub serves. */
        public RespondedTransition respond(ResponseSpec response) {
            return new RespondedTransition(fromState, stub.willReturn(response));
        }
    }

    /** The final step of a scenario transition: names the state reached once it fires. */
    public final class RespondedTransition {

        private final String fromState;
        private final StubSpec stub;

        private RespondedTransition(String fromState, StubSpec stub) {
            this.fromState = fromState;
            this.stub = stub;
        }

        /** Completes the transition, moving the scenario to {@code toState}, and returns the updated scenario. */
        public ScenarioSpec goTo(String toState) {
            return withTransition(new PendingTransition(fromState, stub, toState));
        }
    }
}
