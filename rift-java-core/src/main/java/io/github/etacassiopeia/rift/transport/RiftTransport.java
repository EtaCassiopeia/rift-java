package io.github.etacassiopeia.rift.transport;

import io.github.etacassiopeia.rift.json.JsonValue;

import java.net.URI;
import java.util.Optional;

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

    void deleteImposter(int port);

    void deleteAll();

    JsonValue listImposters(boolean replayable, boolean removeProxies);

    void replaceAllImposters(JsonValue doc);

    JsonValue applyConfig(JsonValue config);

    void addStub(int port, JsonValue stub);

    void replaceStubs(int port, JsonValue stubs);

    void replaceStub(int port, StubAddress addr, JsonValue stub);

    void deleteStub(int port, StubAddress addr);

    JsonValue recorded(int port);

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
