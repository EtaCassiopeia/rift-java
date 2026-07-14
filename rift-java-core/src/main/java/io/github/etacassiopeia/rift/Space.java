package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.dsl.StubSpec;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.Stub;
import io.github.etacassiopeia.rift.verify.RequestMatch;
import io.github.etacassiopeia.rift.verify.VerificationTimes;

import java.util.List;

/** A correlated-isolation "space": a flow-scoped view of an imposter's stubs and recorded traffic. */
public interface Space {

    String flowId();

    StubRef addStub(StubSpec spec);

    /**
     * Raw-JSON form of {@link #addStub(StubSpec)} — {@code stub} is added to this space as-is,
     * preserving fields the DSL cannot express ({@code extra}, {@code _rift}); the engine validates
     * the content.
     */
    StubRef addStub(JsonValue stub);

    List<Stub> stubs();

    List<RecordedRequest> recorded();

    /** Recorded requests within this space, filtered against {@code match}'s predicates. */
    List<RecordedRequest> recorded(RequestMatch match);

    /** Verifies at least one request recorded within this space matched {@code match}'s predicates. */
    void verify(RequestMatch match);

    /** Verifies the number of requests recorded within this space matching {@code match}'s predicates satisfies {@code times}. */
    void verify(RequestMatch match, VerificationTimes times);

    void delete();
}
