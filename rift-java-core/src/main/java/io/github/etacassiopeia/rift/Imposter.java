package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.dsl.StubSpec;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.ImposterDefinition;
import io.github.etacassiopeia.rift.model.Stub;
import io.github.etacassiopeia.rift.verify.RequestMatch;
import io.github.etacassiopeia.rift.verify.VerificationTimes;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/** A thin, transport-backed handle to a live imposter — no local caching; every call round-trips to the engine. */
public interface Imposter {

    int port();

    /** This imposter's own network address, derived from its port via {@code ConnectOptions.hostResolver}. */
    URI uri();

    Optional<String> name();

    /** The imposter's current definition, fetched fresh from the engine. */
    ImposterDefinition definition();

    StubRef addStub(StubSpec spec);

    StubRef addStub(JsonValue stub);

    void replaceStubs(List<StubSpec> specs);

    StubRef stub(String id);

    List<Stub> stubs();

    List<RecordedRequest> recorded();

    /** Recorded requests filtered client-side against {@code match}'s predicates (equals on method/path only; see {@code ImposterImpl}). */
    List<RecordedRequest> recorded(RequestMatch match);

    void clearRecorded();

    void clearProxyResponses();

    Scenarios scenarios();

    Space space(String flowId);

    FlowState flowState(String flowId);

    /** Verifies at least one recorded request matched {@code match}'s predicates. */
    void verify(RequestMatch match);

    /** Verifies the number of recorded requests matching {@code match}'s predicates satisfies {@code times}. */
    void verify(RequestMatch match, VerificationTimes times);

    /** Verifies this imposter recorded no requests at all. */
    void verifyNoInteractions();

    void enable();

    void disable();

    void delete();
}
