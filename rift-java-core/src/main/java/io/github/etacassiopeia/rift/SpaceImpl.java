package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.dsl.StubSpec;
import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.Stub;
import io.github.etacassiopeia.rift.transport.RiftTransport;
import io.github.etacassiopeia.rift.transport.StubAddress;
import io.github.etacassiopeia.rift.verify.PredicateEvaluator;
import io.github.etacassiopeia.rift.verify.RequestMatch;
import io.github.etacassiopeia.rift.verify.VerificationException;
import io.github.etacassiopeia.rift.verify.VerificationTimes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class SpaceImpl implements Space {

    private final int port;
    private final String flowId;
    private final RiftTransport transport;

    SpaceImpl(int port, String flowId, RiftTransport transport) {
        this.port = port;
        this.flowId = flowId;
        this.transport = transport;
    }

    @Override
    public String flowId() {
        return flowId;
    }

    @Override
    public StubRef addStub(StubSpec spec) {
        transport.spaceAddStub(port, flowId, JsonValue.parse(spec.build().toJson()));
        int index = stubs().size() - 1;
        return new StubRefImpl(port, transport, new StubAddress.ByIndex(index));
    }

    @Override
    public List<Stub> stubs() {
        JsonValue result = transport.spaceListStubs(port, flowId);
        List<Stub> out = new ArrayList<>();
        collectStubs(result, out);
        return List.copyOf(out);
    }

    @Override
    public List<RecordedRequest> recorded() {
        JsonValue result = transport.spaceRecorded(port, flowId);
        List<RecordedRequest> out = new ArrayList<>();
        collectRecorded(result, out);
        return List.copyOf(out);
    }

    @Override
    public List<RecordedRequest> recorded(RequestMatch match) {
        return recorded().stream().filter(r -> PredicateEvaluator.matches(r, match.predicates())).toList();
    }

    @Override
    public void verify(RequestMatch match) {
        verify(match, VerificationTimes.atLeast(1));
    }

    @Override
    public void verify(RequestMatch match, VerificationTimes times) {
        // Unlike Imposter.verify, a space is not itself an imposter definition, so there is no
        // recordRequests() flag to check here — recording is configured on the owning imposter.
        PredicateEvaluator.requireNoInject(match.predicates());
        List<RecordedRequest> all = recorded();
        int count = (int) all.stream().filter(r -> PredicateEvaluator.matches(r, match.predicates())).count();
        if (!times.matches(count)) {
            throw new VerificationException(port, Optional.empty(), match, times, count, all);
        }
    }

    @Override
    public void delete() {
        transport.spaceDelete(port, flowId);
    }

    private static void collectStubs(JsonValue result, List<Stub> out) {
        if (result instanceof JsonArray arr) {
            for (JsonValue v : arr.items()) {
                out.add(Stub.fromJson(v.toJson()));
            }
        } else if (result instanceof JsonObject obj && obj.get("stubs") instanceof JsonArray arr) {
            for (JsonValue v : arr.items()) {
                out.add(Stub.fromJson(v.toJson()));
            }
        }
    }

    private static void collectRecorded(JsonValue result, List<RecordedRequest> out) {
        if (result instanceof JsonArray arr) {
            for (JsonValue v : arr.items()) {
                out.add(RecordedRequest.read(v));
            }
        } else if (result instanceof JsonObject obj && obj.get("requests") instanceof JsonArray arr) {
            for (JsonValue v : arr.items()) {
                out.add(RecordedRequest.read(v));
            }
        }
    }
}
