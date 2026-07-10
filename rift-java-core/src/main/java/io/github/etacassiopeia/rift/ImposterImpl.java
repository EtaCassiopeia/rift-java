package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.dsl.ProxySpec;
import io.github.etacassiopeia.rift.dsl.RequestField;
import io.github.etacassiopeia.rift.dsl.RiftDsl;
import io.github.etacassiopeia.rift.dsl.StubSpec;
import io.github.etacassiopeia.rift.error.InvalidDefinition;
import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.ImposterDefinition;
import io.github.etacassiopeia.rift.model.Stub;
import io.github.etacassiopeia.rift.transport.RiftTransport;
import io.github.etacassiopeia.rift.transport.StubAddress;
import io.github.etacassiopeia.rift.verify.PredicateEvaluator;
import io.github.etacassiopeia.rift.verify.RequestMatch;
import io.github.etacassiopeia.rift.verify.VerificationException;
import io.github.etacassiopeia.rift.verify.VerificationTimes;

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
    public Recording startRecording(String originUrl) {
        return startRecording(originUrl, RecordSpec.builder().build());
    }

    @Override
    public Recording startRecording(String originUrl, RecordSpec spec) {
        JsonValue proxyStub = buildProxyStub(originUrl, spec);
        List<JsonValue> existing = definition().stubs().stream()
                .map(s -> (JsonValue) JsonValue.parse(s.toJson())).toList();
        List<JsonValue> reordered = new ArrayList<>();
        // ONCE/TRANSPARENT must match before any existing stub could shadow the proxy — prepend;
        // ALWAYS records new entries as it goes, so appending keeps existing stubs first.
        if (spec.mode() == RecordMode.ALWAYS) {
            reordered.addAll(existing);
            reordered.add(proxyStub);
        } else {
            reordered.add(proxyStub);
            reordered.addAll(existing);
        }
        transport.replaceStubs(port, new JsonArray(reordered));
        return new RecordingImpl(port, transport, originUrl, spec);
    }

    private static JsonValue buildProxyStub(String originUrl, RecordSpec spec) {
        ProxySpec proxy = RiftDsl.proxyTo(originUrl);
        proxy = switch (spec.mode()) {
            case ONCE -> proxy.proxyOnce();
            case ALWAYS -> proxy.proxyAlways();
            case TRANSPARENT -> proxy.proxyTransparent();
        };
        proxy = proxy.generateBy(spec.generators().toArray(new RequestField[0]));
        if (spec.addWaitBehavior()) {
            proxy = proxy.addWaitBehavior();
        }
        Stub stub = RiftDsl.onRequest().willReturn(proxy).build();
        return JsonValue.parse(stub.toJson());
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
        return recorded().stream().filter(r -> PredicateEvaluator.matches(r, match.predicates())).toList();
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

    @Override
    public void verify(RequestMatch match) {
        verify(match, VerificationTimes.atLeast(1));
    }

    @Override
    public void verify(RequestMatch match, VerificationTimes times) {
        if (!definition().recordRequests()) {
            throw new InvalidDefinition("imposter :" + port + " does not record requests — add .record()");
        }
        // Reject an inject predicate up front: PredicateEvaluator.matches only reaches it once every
        // preceding AND-clause has matched, so relying on the per-request filter below alone could
        // silently skip the rejection for a request set where an earlier clause never matches.
        PredicateEvaluator.requireNoInject(match.predicates());
        List<RecordedRequest> all = recorded();
        int count = (int) all.stream().filter(r -> PredicateEvaluator.matches(r, match.predicates())).count();
        if (!times.matches(count)) {
            throw new VerificationException(port, name(), match, times, count, all);
        }
    }

    @Override
    public void verifyNoInteractions() {
        if (!definition().recordRequests()) {
            throw new InvalidDefinition("imposter :" + port + " does not record requests — add .record()");
        }
        List<RecordedRequest> all = recorded();
        if (!all.isEmpty()) {
            RequestMatch noPredicates = List::of;
            throw new VerificationException(port, name(), noPredicates, VerificationTimes.never(), all.size(), all);
        }
    }
}
