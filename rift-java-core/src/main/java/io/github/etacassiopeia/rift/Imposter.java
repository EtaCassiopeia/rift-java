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

    /**
     * Inserts a stub at {@code index} (0 = first, highest match priority — first-match-wins). Since
     * matching is first-match-wins, {@code index 0} temporarily overrides an existing stub; delete the
     * returned {@link StubRef} to revert. {@code index} must be in {@code [0, stubCount]}.
     */
    StubRef addStub(StubSpec spec, int index);

    /** Inserts a stub at the front (highest priority) — sugar for {@code addStub(spec, 0)}; the overlay idiom. */
    StubRef addStubFirst(StubSpec spec);

    StubRef addStub(JsonValue stub);

    /**
     * Raw-JSON form of {@link #addStub(StubSpec, int)} — the escape hatch for callers holding a
     * fully-formed stub (an SDK bridge, a golden file). {@code stub} is passed to the engine as-is:
     * fields the DSL cannot express ({@code extra}, {@code _rift}) survive, and the engine — not this
     * SDK — validates the content. {@code index} must be in {@code [0, stubCount]}.
     */
    StubRef addStub(JsonValue stub, int index);

    /** Raw-JSON form of {@link #addStubFirst(StubSpec)} — sugar for {@code addStub(stub, 0)}. */
    StubRef addStubFirst(JsonValue stub);

    /** Starts a proxy recording to {@code originUrl} with the default {@link RecordSpec}. */
    Recording startRecording(String originUrl);

    /** Starts a proxy recording to {@code originUrl}, configured by {@code spec}. */
    Recording startRecording(String originUrl, RecordSpec spec);

    void replaceStubs(List<StubSpec> specs);

    /**
     * Raw-JSON form of {@link #replaceStubs(List)} — {@code stubs} must be a {@code JsonArray} of stub
     * objects, passed to the engine as-is (see {@link #addStub(JsonValue, int)} for the pass-through
     * contract). Takes a single {@code JsonValue} rather than a {@code List<JsonValue>} because the
     * latter would erase to the same signature as {@link #replaceStubs(List)}.
     *
     * @throws io.github.etacassiopeia.rift.error.InvalidDefinition if {@code stubs} is not a JSON array
     */
    void replaceStubs(JsonValue stubs);

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
