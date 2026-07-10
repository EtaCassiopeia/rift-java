package io.github.etacassiopeia.rift.embedded;

import io.github.etacassiopeia.rift.error.EngineError;
import io.github.etacassiopeia.rift.json.JsonNumber;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.transport.RemoteTransport;
import io.github.etacassiopeia.rift.transport.RiftTransport;
import io.github.etacassiopeia.rift.transport.StubAddress;

import java.lang.foreign.Arena;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link RiftTransport} over the in-process rift engine via the Panama FFM C-ABI v2. Imposter
 * lifecycle, recording, flow-state, and spaces are driven directly through {@code librift_ffi};
 * the operations with no direct C-ABI entry point (imposter listing, per-stub edits, scenarios,
 * enable/disable) are delegated to a lazily-started in-process admin server over {@link
 * RemoteTransport}.
 */
public final class EmbeddedTransport implements RiftTransport {

    private final Arena libArena;
    private final FfiCalls calls;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Object adminLock = new Object();
    private volatile RemoteTransport admin;

    private EmbeddedTransport(Arena libArena, FfiCalls calls) {
        this.libArena = libArena;
        this.calls = calls;
    }

    public static EmbeddedTransport open(Path lib) {
        Arena libArena = Arena.ofShared();
        RiftFfi ffi = RiftFfi.load(lib, libArena);
        FfiCalls calls = FfiCalls.start(ffi);
        return new EmbeddedTransport(libArena, calls);
    }

    @Override
    public JsonValue createImposter(JsonValue def) {
        // A faithful, uniform wire mapping: the def is passed through untouched so embedded behaves
        // identically to the remote/spawn transports for the same definition. (The engine backs
        // flow-state/spaces with a real store only when the def declares one, e.g. _rift.flowState —
        // that is uniform engine behaviour, not something a transport should paper over. See #40.)
        int port = calls.createImposter(def);
        return JsonObject.builder().put("port", JsonNumber.of(port)).build();
    }

    @Override
    public JsonValue getImposter(int port) {
        return admin().getImposter(port);
    }

    @Override
    public void deleteImposter(int port) {
        calls.deleteImposter(port);
    }

    @Override
    public void deleteAll() {
        calls.deleteAll();
    }

    @Override
    public JsonValue listImposters(boolean replayable, boolean removeProxies) {
        return admin().listImposters(replayable, removeProxies);
    }

    @Override
    public void replaceAllImposters(JsonValue doc) {
        admin().replaceAllImposters(doc);
    }

    @Override
    public JsonValue applyConfig(JsonValue config) {
        return calls.applyConfig(config);
    }

    @Override
    public void addStub(int port, JsonValue stub) {
        admin().addStub(port, stub);
    }

    @Override
    public void replaceStubs(int port, JsonValue stubs) {
        calls.replaceStubs(port, stubs);
    }

    @Override
    public void replaceStub(int port, StubAddress addr, JsonValue stub) {
        admin().replaceStub(port, addr, stub);
    }

    @Override
    public void deleteStub(int port, StubAddress addr) {
        admin().deleteStub(port, addr);
    }

    @Override
    public JsonValue recorded(int port) {
        return calls.recorded(port);
    }

    @Override
    public void clearRecorded(int port) {
        admin().clearRecorded(port);
    }

    @Override
    public void clearProxyResponses(int port) {
        admin().clearProxyResponses(port);
    }

    @Override
    public void enable(int port) {
        admin().enable(port);
    }

    @Override
    public void disable(int port) {
        admin().disable(port);
    }

    @Override
    public JsonValue scenarios(int port, Optional<String> flowId) {
        return admin().scenarios(port, flowId);
    }

    @Override
    public void setScenarioState(int port, String name, String state) {
        admin().setScenarioState(port, name, state);
    }

    @Override
    public void resetScenarios(int port) {
        admin().resetScenarios(port);
    }

    @Override
    public Optional<JsonValue> flowStateGet(int port, String flowId, String key) {
        return calls.flowStateGet(port, flowId, key);
    }

    @Override
    public void flowStatePut(int port, String flowId, String key, JsonValue value) {
        calls.flowStatePut(port, flowId, key, value);
    }

    @Override
    public void flowStateDelete(int port, String flowId, String key) {
        calls.flowStateDelete(port, flowId, key);
    }

    @Override
    public void spaceAddStub(int port, String flowId, JsonValue stub) {
        calls.spaceAddStub(port, flowId, stub);
    }

    @Override
    public JsonValue spaceListStubs(int port, String flowId) {
        return calls.spaceListStubs(port, flowId);
    }

    @Override
    public JsonValue spaceRecorded(int port, String flowId) {
        return calls.spaceRecorded(port, flowId);
    }

    @Override
    public void spaceDelete(int port, String flowId) {
        calls.spaceDelete(port, flowId);
    }

    @Override
    public JsonValue buildInfo() {
        return calls.buildInfo();
    }

    // Intercept is driven directly over the C-ABI (no admin loopback needed).

    @Override
    public JsonValue startIntercept(JsonValue options) {
        return calls.startIntercept(options);
    }

    @Override
    public void interceptAddRules(JsonValue rules) {
        calls.interceptAddRules(rules);
    }

    @Override
    public JsonValue interceptListRules() {
        return calls.interceptListRules();
    }

    @Override
    public void interceptClearRules() {
        calls.interceptClearRules();
    }

    @Override
    public String interceptCaPem() {
        return calls.interceptCaPem();
    }

    @Override
    public URI adminUri() {
        return admin().adminUri();
    }

    private RemoteTransport admin() {
        RemoteTransport a = admin;
        if (a != null) {
            return a;
        }
        synchronized (adminLock) {
            a = admin;
            if (a == null) {
                JsonValue result = calls.serveAdmin(JsonObject.builder().put("port", JsonNumber.of(0)).build());
                if (!(result instanceof JsonObject obj && obj.get("adminUrl") instanceof JsonString adminUrl)) {
                    throw new EngineError(-1, "rift_serve_admin did not return an adminUrl: " + result.toJson());
                }
                a = new RemoteTransport(URI.create(adminUrl.value()), Optional.empty(), Duration.ofSeconds(30));
                admin = a;
            }
        }
        return a;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        // Always release the engine handle and unload the library even if the admin client's close
        // throws, otherwise a failure there would leak the native handle and the mapped library.
        try {
            RemoteTransport a = admin;
            if (a != null) {
                a.close();
            }
        } finally {
            try {
                calls.stop();
            } finally {
                libArena.close();
            }
        }
    }
}
