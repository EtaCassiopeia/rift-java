package io.github.etacassiopeia.rift.transport;

import io.github.etacassiopeia.rift.json.JsonValue;

import java.net.URI;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The internal SPI a {@code Rift} client speaks against: one call per admin-API operation, with
 * bodies/results as raw {@link JsonValue} — the caller (in {@code io.github.etacassiopeia.rift})
 * owns translating to/from the typed wire model. Implementations map every failure mode to the
 * {@code io.github.etacassiopeia.rift.error} hierarchy; none of these methods throw a checked
 * exception or return {@code null}.
 *
 * <p>{@link #close()} is idempotent and releases any transport-owned resources (e.g. an HTTP
 * client, a spawned engine process).
 */
public interface RiftTransport extends AutoCloseable {

    JsonValue createImposter(JsonValue def);

    JsonValue getImposter(int port);

    /**
     * The imposter definition, optionally exported in replayable form ({@code replayable=true})
     * and/or with any proxy responses removed ({@code removeProxies=true}). Non-mutating even when
     * both flags are set — the underlying admin-API call is a plain {@code GET}.
     */
    JsonValue getImposter(int port, boolean replayable, boolean removeProxies);

    void deleteImposter(int port);

    void deleteAll();

    JsonValue listImposters(boolean replayable, boolean removeProxies);

    void replaceAllImposters(JsonValue doc);

    JsonValue applyConfig(JsonValue config);

    void addStub(int port, JsonValue stub);

    /**
     * Inserts {@code stub} at {@code index} (0 = first / highest match priority). The default falls
     * back to {@link #addStub(int, JsonValue)} (ignoring the position) so existing test fakes keep
     * compiling; the real transports override with a positional insert.
     */
    default void addStub(int port, JsonValue stub, int index) {
        addStub(port, stub);
    }

    void replaceStubs(int port, JsonValue stubs);

    void replaceStub(int port, StubAddress addr, JsonValue stub);

    void deleteStub(int port, StubAddress addr);

    /** The single stub addressed by {@code addr}, as its bare (unwrapped) JSON representation. */
    default JsonValue getStub(int port, StubAddress addr) {
        throw new UnsupportedOperationException("getStub");
    }

    JsonValue recorded(int port);

    /**
     * One page of {@code savedRequests} plus its cursor metadata, still as raw JSON — the facade
     * owns the typed translation.
     *
     * @param nextIndex the {@code x-rift-next-index} cursor, or empty when the transport did not
     *                  report one (see {@link #recordedSince})
     * @param truncated the {@code x-rift-truncated} signal: retention evicted unseen entries
     */
    record RecordedSlice(JsonValue requests, OptionalLong nextIndex, boolean truncated) {}

    /**
     * Reads {@code savedRequests} with an optional journal cursor (rift#603): entries strictly newer
     * than {@code since}, or everything retained when {@code since} is empty. The two are different
     * questions, not one with a default — a baseline read never reports truncation, while
     * {@code since=0} ("replay everything") does once anything has been evicted.
     *
     * <p>The default serves the full list and reports no cursor, which is exactly what an engine
     * without cursor support does over HTTP. It is the honest answer for any transport that cannot
     * see response headers — notably the in-process FFI one, where there is no HTTP response to read
     * and rift#603 expects consumers to poll. Synthesizing an index from an array offset here would
     * re-introduce the skip-entries bug the cursor exists to remove, so implementations that cannot
     * obtain a real cursor must leave {@code nextIndex} empty rather than invent one.
     */
    default RecordedSlice recordedSince(int port, OptionalLong since) {
        return new RecordedSlice(recorded(port), OptionalLong.empty(), false);
    }

    void clearRecorded(int port);

    void clearProxyResponses(int port);

    void enable(int port);

    void disable(int port);

    JsonValue scenarios(int port, Optional<String> flowId);

    void setScenarioState(int port, String name, String state);

    void resetScenarios(int port);

    Optional<JsonValue> flowStateGet(int port, String flowId, String key);

    void flowStatePut(int port, String flowId, String key, JsonValue value);

    void flowStateDelete(int port, String flowId, String key);

    void spaceAddStub(int port, String flowId, JsonValue stub);

    JsonValue spaceListStubs(int port, String flowId);

    JsonValue spaceRecorded(int port, String flowId);

    void spaceDelete(int port, String flowId);

    JsonValue buildInfo();

    URI adminUri();

    /**
     * Counts recorded requests matching a predicate set server-side: {@code body} is the same
     * {@code POST /imposters/{port}/verify} request body, {@code {"predicates":[…],"flowId"?,
     * "includeRequests"?,"includeClosest"?}}; the result is the {@code {"matched","total",
     * "requests"?,"closest"?}} envelope.
     */
    default JsonValue verify(int port, JsonValue body) {
        throw new UnsupportedOperationException("verify");
    }

    /** The stub-overlap analysis warnings (duplicate/shadowed/catch-all stubs) for {@code port}, as a JSON array. */
    default JsonValue stubWarnings(int port) {
        throw new UnsupportedOperationException("stubWarnings");
    }

    /**
     * Starts the intercept (TLS-MITM) listener with the given {@code {host,port,caCertPath,
     * caKeyPath}} options, returning {@code {interceptPort,interceptUrl}}. One intercept per
     * engine; the caller (see {@code RiftImpl}) enforces that invariant before this is ever
     * called, so a transport need not guard against a second call itself.
     */
    JsonValue startIntercept(JsonValue options);

    /** Adds one ({@code JsonObject}) or many ({@code JsonArray}) intercept rules. */
    void interceptAddRules(JsonValue rules);

    /** The current intercept rules, as a JSON array. */
    JsonValue interceptListRules();

    void interceptClearRules();

    /** The intercept CA certificate, PEM-encoded. */
    String interceptCaPem();

    @Override
    void close();
}
