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
 * #startingAt(String)} (if any) is written as <em>no</em> {@code requiredScenarioState}, matching
 * the engine's convention that a scenario with no recorded state is in its starting state.
 *
 * <p>Instances are immutable: {@link #startingAt(String)} and each completed transition return a
 * new {@code ScenarioSpec}. The terminal {@link #stubs()} returns the built {@link Stub} list, for
 * use with {@link ImposterSpec#stub(List)}.
 */
public final class ScenarioSpec {

    private final String name;
    private final String startState;
    private final List<Stub> stubs;

    ScenarioSpec(String name) {
        this(name, null, List.of());
    }

    private ScenarioSpec(String name, String startState, List<Stub> stubs) {
        this.name = name;
        this.startState = startState;
        this.stubs = stubs;
    }

    /** Names the scenario's starting state, so transitions out of it omit {@code requiredScenarioState}. */
    public ScenarioSpec startingAt(String state) {
        return new ScenarioSpec(name, state, stubs);
    }

    /** Begins a transition out of {@code fromState}, gated by the given seed stub's predicates. */
    public Transition when(String fromState, StubSpec stub) {
        return new Transition(fromState, stub);
    }

    /** All transitions added so far, as built {@link Stub} values, in the order they were added. */
    public List<Stub> stubs() {
        return stubs;
    }

    private ScenarioSpec withStub(Stub stub) {
        return new ScenarioSpec(name, startState, Stream.concat(stubs.stream(), Stream.of(stub)).toList());
    }

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
            Optional<String> required = fromState.equals(startState) ? Optional.empty() : Optional.of(fromState);
            return withStub(stub.withScenarioTransition(name, required, toState).build());
        }
    }
}
