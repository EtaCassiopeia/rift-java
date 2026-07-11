package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.dsl.ProxySpec;
import io.github.etacassiopeia.rift.dsl.RequestField;
import io.github.etacassiopeia.rift.dsl.RiftDsl;
import io.github.etacassiopeia.rift.dsl.StubSpec;
import io.github.etacassiopeia.rift.error.InvalidDefinition;
import io.github.etacassiopeia.rift.error.RiftException;
import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.FlowStateSupport;
import io.github.etacassiopeia.rift.model.ImposterDefinition;
import io.github.etacassiopeia.rift.model.Stub;
import io.github.etacassiopeia.rift.transport.RiftTransport;
import io.github.etacassiopeia.rift.transport.StubAddress;
import io.github.etacassiopeia.rift.verify.PredicateEvaluator;
import io.github.etacassiopeia.rift.verify.RequestMatch;
import io.github.etacassiopeia.rift.verify.VerificationException;
import io.github.etacassiopeia.rift.verify.VerificationTimes;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ImposterImpl implements Imposter {

    private static final System.Logger LOG = System.getLogger(ImposterImpl.class.getName());

    private final int port;
    private final RiftTransport transport;
    private final ConnectOptions options;
    private boolean spaceConfigChecked;
    private boolean flowStateConfigChecked;

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
        warnIfSpacesUnusable();
        return new SpaceImpl(port, flowId, transport);
    }

    @Override
    public FlowState flowState(String flowId) {
        warnIfFlowStateUnusable();
        return new FlowStateImpl(port, flowId, transport);
    }

    /**
     * Warn (once) if this imposter uses spaces but declares no header-form {@code flowIdSource}: the
     * engine's flow-id source defaults to the port, so a space stub can never match (#40). Advisory —
     * the def is fetched once and a fetch failure is swallowed to DEBUG rather than break the accessor.
     */
    private synchronized void warnIfSpacesUnusable() {
        if (spaceConfigChecked) {
            return;
        }
        spaceConfigChecked = true;
        try {
            if (!FlowStateSupport.hasHeaderFlowIdSource(definition())) {
                LOG.log(Level.WARNING, "imposter on port " + port + " uses spaces but declares no header "
                        + "flow-id source; space stubs can never match (engine flow-id default is imposter_port). "
                        + "Declare flowState(inMemoryFlowState().flowIdFromHeader(\"X-Your-Header\")).");
            }
        } catch (RiftException e) {
            // Advisory only: the SPI's declared def-fetch failures (engine unavailable, not found,
            // unparseable body) downgrade to DEBUG rather than break the accessor. A non-RiftException
            // (a bug, or misuse like a closed transport) is left to propagate loudly.
            LOG.log(Level.DEBUG, "could not evaluate flow-state config for port " + port, e);
        }
    }

    /**
     * Warn (once) if the flow-state API is used on an imposter with no store trigger (an explicit
     * {@code _rift.flowState}, a scenario stub, or a {@code _rift.script} stub): the engine uses a
     * no-op store, so reads return empty (#40). Advisory, same fetch-once/swallow policy as above.
     */
    private synchronized void warnIfFlowStateUnusable() {
        if (flowStateConfigChecked) {
            return;
        }
        flowStateConfigChecked = true;
        try {
            if (!FlowStateSupport.hasStoreTrigger(definition())) {
                LOG.log(Level.WARNING, "imposter on port " + port + " uses the flow-state API but declares no "
                        + "store trigger (_rift.flowState, a scenario stub, or a _rift.script stub); reads return "
                        + "empty (engine uses a no-op store). Declare flowState(inMemoryFlowState()).");
            }
        } catch (RiftException e) {
            // Advisory only: the SPI's declared def-fetch failures (engine unavailable, not found,
            // unparseable body) downgrade to DEBUG rather than break the accessor. A non-RiftException
            // (a bug, or misuse like a closed transport) is left to propagate loudly.
            LOG.log(Level.DEBUG, "could not evaluate flow-state config for port " + port, e);
        }
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
