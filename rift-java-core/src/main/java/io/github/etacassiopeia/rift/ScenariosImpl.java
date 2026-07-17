package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.error.ImposterNotFound;
import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.transport.RiftTransport;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ScenariosImpl implements Scenarios {

    private final int port;
    private final RiftTransport transport;

    ScenariosImpl(int port, RiftTransport transport) {
        this.port = port;
        this.transport = transport;
    }

    @Override
    public List<State> list() {
        return read(Optional.empty());
    }

    @Override
    public List<State> list(String flowId) {
        return read(Optional.of(flowId));
    }

    @Override
    public String state(String name) {
        return list().stream()
                .filter(s -> s.name().equals(name))
                .map(State::state)
                .findFirst()
                .orElseThrow(() -> new ImposterNotFound(port, "no scenario named '" + name + "'"));
    }

    @Override
    public void setState(String name, String state) {
        transport.setScenarioState(port, name, state, Optional.empty());
    }

    @Override
    public void setState(String name, String state, String flowId) {
        transport.setScenarioState(port, name, state, Optional.of(flowId));
    }

    @Override
    public void reset() {
        transport.resetScenarios(port);
    }

    private List<State> read(Optional<String> flowId) {
        JsonValue result = transport.scenarios(port, flowId);
        List<State> out = new ArrayList<>();
        for (JsonValue v : scenarioArray(result).items()) {
            if (v instanceof JsonObject obj
                    && obj.get("name") instanceof JsonString name
                    && obj.get("state") instanceof JsonString state) {
                out.add(new State(name.value(), state.value()));
            }
        }
        return List.copyOf(out);
    }

    /** The engine answers {@code {"scenarios":[{name,state}]}}; also tolerate a bare {@code [{name,state}]} array. */
    private static JsonArray scenarioArray(JsonValue result) {
        if (result instanceof JsonObject obj && obj.get("scenarios") instanceof JsonArray arr) {
            return arr;
        }
        if (result instanceof JsonArray arr) {
            return arr;
        }
        return new JsonArray(List.of());
    }
}
