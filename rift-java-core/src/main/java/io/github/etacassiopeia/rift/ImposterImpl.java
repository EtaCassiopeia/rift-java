package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.dsl.StubSpec;
import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.ImposterDefinition;
import io.github.etacassiopeia.rift.model.Predicate;
import io.github.etacassiopeia.rift.model.PredicateOperation;
import io.github.etacassiopeia.rift.model.Stub;
import io.github.etacassiopeia.rift.transport.RiftTransport;
import io.github.etacassiopeia.rift.transport.StubAddress;
import io.github.etacassiopeia.rift.verify.RequestMatch;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ImposterImpl implements Imposter {

    private final int port;
    private final RiftTransport transport;
    private final ConnectOptions options;

    ImposterImpl(int port, RiftTransport transport, ConnectOptions options) {
        this.port = port;
        this.transport = transport;
        this.options = options;
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public URI uri() {
        return options.hostResolver().apply(port);
    }

    @Override
    public Optional<String> name() {
        return definition().name();
    }

    @Override
    public ImposterDefinition definition() {
        return ImposterDefinition.fromJson(transport.getImposter(port).toJson());
    }

    @Override
    public StubRef addStub(StubSpec spec) {
        return addStub(JsonValue.parse(spec.build().toJson()));
    }

    @Override
    public StubRef addStub(JsonValue stub) {
        transport.addStub(port, stub);
        int index = definition().stubs().size() - 1;
        return new StubRefImpl(port, transport, new StubAddress.ByIndex(index));
    }

    @Override
    public void replaceStubs(List<StubSpec> specs) {
        JsonArray stubs = new JsonArray(specs.stream().map(s -> (JsonValue) JsonValue.parse(s.build().toJson())).toList());
        transport.replaceStubs(port, stubs);
    }

    @Override
    public StubRef stub(String id) {
        return new StubRefImpl(port, transport, new StubAddress.ById(id));
    }

    @Override
    public List<Stub> stubs() {
        return definition().stubs();
    }

    @Override
    public List<RecordedRequest> recorded() {
        JsonValue result = transport.recorded(port);
        List<RecordedRequest> out = new ArrayList<>();
        if (result instanceof JsonArray arr) {
            for (JsonValue v : arr.items()) {
                out.add(RecordedRequest.read(v));
            }
        } else if (result instanceof JsonObject obj && obj.get("requests") instanceof JsonArray arr) {
            for (JsonValue v : arr.items()) {
                out.add(RecordedRequest.read(v));
            }
        }
        return List.copyOf(out);
    }

    @Override
    public List<RecordedRequest> recorded(RequestMatch match) {
        return recorded().stream().filter(r -> matchesAll(r, match.predicates())).toList();
    }

    @Override
    public void clearRecorded() {
        transport.clearRecorded(port);
    }

    @Override
    public void clearProxyResponses() {
        transport.clearProxyResponses(port);
    }

    @Override
    public Scenarios scenarios() {
        return new ScenariosImpl(port, transport);
    }

    @Override
    public Space space(String flowId) {
        return new SpaceImpl(port, flowId, transport);
    }

    @Override
    public FlowState flowState(String flowId) {
        return new FlowStateImpl(port, flowId, transport);
    }

    @Override
    public void enable() {
        transport.enable(port);
    }

    @Override
    public void disable() {
        transport.disable(port);
    }

    @Override
    public void delete() {
        transport.deleteImposter(port);
    }

    // Full predicate evaluation (PredicateEvaluator) is out of scope here — issue #6. This covers
    // only the common case of a flat `equals` predicate on method/path, which is what every stub
    // seeded by RiftDsl.onGet/onPost/... carries; any other operation is treated as satisfied
    // (i.e. not filtered on), so callers get a superset rather than a silently wrong subset.
    private static boolean matchesAll(RecordedRequest request, List<Predicate> predicates) {
        for (Predicate predicate : predicates) {
            if (!matches(request, predicate)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(RecordedRequest request, Predicate predicate) {
        if (!(predicate.operation() instanceof PredicateOperation.Equals equals)) {
            return true;
        }
        for (var field : equals.fields().entrySet()) {
            if (field.getKey().equals("method") && !fieldEquals(field.getValue(), request.method(), true)) {
                return false;
            }
            if (field.getKey().equals("path") && !fieldEquals(field.getValue(), request.path(), false)) {
                return false;
            }
        }
        return true;
    }

    private static boolean fieldEquals(JsonValue expected, String actual, boolean ignoreCase) {
        if (!(expected instanceof JsonString s)) {
            return true;
        }
        return ignoreCase ? s.value().equalsIgnoreCase(actual) : s.value().equals(actual);
    }
}
