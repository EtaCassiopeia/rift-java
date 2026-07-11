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

    /** Starts a proxy recording to {@code originUrl} with the default {@link RecordSpec}. */
    Recording startRecording(String originUrl);

    /** Starts a proxy recording to {@code originUrl}, configured by {@code spec}. */
    Recording startRecording(String originUrl, RecordSpec spec);

    void replaceStubs(List<StubSpec> specs);

    StubRef stub(String id);

    List<Stub> stubs();

    List<RecordedRequest> recorded();

    /** Recorded requests filtered client-side against {@code match}'s predicates (equals on method/path only; see {@code ImposterImpl}). */
    List<RecordedRequest> recorded(RequestMatch match);

    void clearRecorded();

    void clearProxyResponses();

    Scenarios scenarios();

    /**
     * A per-space (correlated-isolation) view. Spaces require the imposter to declare a header-form
     * {@code flowIdSource} ({@code flowIdFromHeader}); the engine's flow-id source otherwise defaults
     * to the port and space stubs never match. This accessor logs one advisory warning if that
     * configuration is missing (it never throws — admin-only list/delete workflows remain valid).
     */
    Space space(String flowId);

    /**
     * The runtime flow-state store for a flow id. The engine backs this with a real store only when
     * the def declares one — an explicit {@code _rift.flowState}, a scenario stub, or a {@code
     * _rift.script} stub; otherwise reads return empty (a no-op store). This accessor logs one
     * advisory warning if no such trigger is present.
     */
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
