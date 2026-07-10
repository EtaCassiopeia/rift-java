package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.dsl.StubSpec;
import io.github.etacassiopeia.rift.model.Stub;

import java.util.List;

/** A correlated-isolation "space": a flow-scoped view of an imposter's stubs and recorded traffic. */
public interface Space {

    String flowId();

    StubRef addStub(StubSpec spec);

    List<Stub> stubs();

    List<RecordedRequest> recorded();

    void delete();
}
