package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.dsl.StubSpec;
import io.github.etacassiopeia.rift.error.ImposterNotFound;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.ImposterDefinition;
import io.github.etacassiopeia.rift.model.Stub;
import io.github.etacassiopeia.rift.transport.RiftTransport;
import io.github.etacassiopeia.rift.transport.StubAddress;

import java.util.List;
import java.util.Optional;

final class StubRefImpl implements StubRef {

    private final int port;
    private final RiftTransport transport;
    private final StubAddress address;

    StubRefImpl(int port, RiftTransport transport, StubAddress address) {
        this.port = port;
        this.transport = transport;
        this.address = address;
    }

    @Override
    public int index() {
        if (address instanceof StubAddress.ByIndex idx) {
            return idx.index();
        }
        return indexOfId(((StubAddress.ById) address).id());
    }

    @Override
    public Optional<String> id() {
        if (address instanceof StubAddress.ById byId) {
            return Optional.of(byId.id());
        }
        return currentStub().id();
    }

    @Override
    public Stub definition() {
        return currentStub();
    }

    @Override
    public void replace(StubSpec spec) {
        transport.replaceStub(port, address, JsonValue.parse(spec.build().toJson()));
    }

    @Override
    public void delete() {
        transport.deleteStub(port, address);
    }

    private Stub currentStub() {
        List<Stub> stubs = fetchStubs();
        if (address instanceof StubAddress.ByIndex idx) {
            return stubs.get(idx.index());
        }
        String id = ((StubAddress.ById) address).id();
        return stubs.stream()
                .filter(s -> s.id().isPresent() && s.id().get().equals(id))
                .findFirst()
                .orElseThrow(() -> new ImposterNotFound(port, "no stub with id '" + id + "'"));
    }

    private int indexOfId(String id) {
        List<Stub> stubs = fetchStubs();
        for (int i = 0; i < stubs.size(); i++) {
            if (stubs.get(i).id().isPresent() && stubs.get(i).id().get().equals(id)) {
                return i;
            }
        }
        throw new ImposterNotFound(port, "no stub with id '" + id + "'");
    }

    private List<Stub> fetchStubs() {
        return ImposterDefinition.fromJson(transport.getImposter(port).toJson()).stubs();
    }
}
