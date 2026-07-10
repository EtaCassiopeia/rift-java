package io.github.etacassiopeia.rift;

import java.util.List;

/** An imposter's scenario finite-state machine: the current state of each named scenario. */
public interface Scenarios {

    /** A scenario's current state, as reported by the engine. */
    record State(String name, String state) {
    }

    /** All scenario states, across every flow/space. */
    List<State> list();

    /** Scenario states scoped to a single flow/space. */
    List<State> list(String flowId);

    /** The current state of the named scenario. */
    String state(String name);

    /** Forces the named scenario into {@code state}. */
    void setState(String name, String state);

    /** Resets every scenario on this imposter back to its initial state. */
    void reset();
}
