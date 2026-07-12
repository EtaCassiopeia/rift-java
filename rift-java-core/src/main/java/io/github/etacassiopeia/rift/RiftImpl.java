package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.dsl.ImposterSpec;
import io.github.etacassiopeia.rift.error.CommunicationError;
import io.github.etacassiopeia.rift.error.EngineUnavailable;
import io.github.etacassiopeia.rift.error.ImposterNotFound;
import io.github.etacassiopeia.rift.error.RiftException;
import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonNumber;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.ImposterDefinition;
import io.github.etacassiopeia.rift.transport.RemoteTransport;
import io.github.etacassiopeia.rift.transport.RiftTransport;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

final class RiftImpl implements Rift {

    /** The oldest engine version this SDK is known to work against; see the version preflight. */
    static final String MIN_ENGINE_VERSION = "0.13.1";

    private static final System.Logger LOG = System.getLogger(RiftImpl.class.getName());

    private final RiftTransport transport;
    private final ConnectOptions options;
    private final Runnable onClose;
    private final AtomicBoolean interceptStarted = new AtomicBoolean(false);

    private RiftImpl(RiftTransport transport, ConnectOptions options, Runnable onClose) {
        this.transport = transport;
        this.options = options;
        this.onClose = onClose;
    }

    static Rift connect(ConnectOptions options) {
        RiftTransport transport = new RemoteTransport(options.adminUri(), options.apiKey(), options.requestTimeout());
        if (options.versionCheck() != VersionCheck.OFF) {
            preflight(transport, options.versionCheck(), false);
        }
        return new RiftImpl(transport, options, () -> { });
    }

    /**
     * Wraps an already-running transport (e.g. a freshly spawned process whose engine version is
     * pinned by the SDK, so no preflight is needed) with an extra {@code onClose} action run after
     * the transport itself is closed — {@link Rift#spawn(SpawnOptions)} uses this to also stop the
     * process it launched.
     */
    static Rift spawned(RiftTransport transport, ConnectOptions options, Runnable onClose) {
        return new RiftImpl(transport, options, onClose);
    }

    /**
     * Wraps an in-process ({@code rift-java-embedded}) transport. Unlike {@link #spawned}, the
     * loaded cdylib's version is whatever the caller pointed {@code EmbeddedOptions} at — it isn't
     * pinned by the SDK the way a downloaded/spawned binary is — so the preflight still runs here
     * unless the caller opted out with {@code VersionCheck.OFF}.
     */
    static Rift embedded(RiftTransport transport, EmbeddedOptions options, Runnable onClose) {
        if (options.versionCheck() != VersionCheck.OFF) {
            // start() already loaded the native library and opened the transport. A version mismatch is
            // a first-class outcome here, so release those native resources before propagating.
            try {
                preflight(transport, options.versionCheck(), true);
            } catch (RuntimeException e) {
                try {
                    transport.close();
                } finally {
                    onClose.run();
                }
                throw e;
            }
        }
        // adminUri is a required constructor arg but is never read for an embedded transport (the
        // transport already exists; RiftImpl#adminUri() delegates to transport.adminUri(), not this
        // options object) — the hostResolver override below is what actually determines an
        // imposter's uri(), so any placeholder value here is inert.
        ConnectOptions.Builder builder = ConnectOptions
                .builder(URI.create("http://" + options.adminHost() + ":" + options.adminPort()))
                .versionCheck(VersionCheck.OFF)
                .hostResolver(port -> URI.create("http://" + options.adminHost() + ":" + port));
        options.apiKey().ifPresent(builder::apiKey);
        return new RiftImpl(transport, builder.build(), onClose);
    }

    /** The outcome of comparing a reported engine version against the floor. Package-private for testing. */
    enum PreflightDecision { PASS, WARN, FAIL }

    /**
     * Decides how to react to a reported {@code version} below {@link #MIN_ENGINE_VERSION}. When
     * {@code abiVerified} (the embedded transport, whose C-ABI symbol set has already been validated
     * by {@code RiftFfi.bind}), the ABI is authoritative: a below-floor version string is a mislabeled
     * dev build (the engine workspace ships a {@code 0.1.0} placeholder), so it is demoted to a warning
     * even in {@code FAIL} mode. A real old engine would have failed the symbol gate first, with a
     * clearer message. Remote/spawn have no symbol gate, so they keep the strict compare.
     */
    static PreflightDecision decide(String version, VersionCheck mode, boolean abiVerified) {
        if (compareSemver(version, MIN_ENGINE_VERSION) >= 0) {
            return PreflightDecision.PASS;
        }
        if (abiVerified) {
            return PreflightDecision.WARN;
        }
        return mode == VersionCheck.FAIL ? PreflightDecision.FAIL : PreflightDecision.WARN;
    }

    private static void preflight(RiftTransport transport, VersionCheck mode, boolean abiVerified) {
        String version;
        try {
            // extractVersion is inside the try so a malformed /config body (CommunicationError) is
            // downgraded to a warning in WARN mode too — WARN must never hard-fail, whatever the cause.
            version = extractVersion(transport.buildInfo());
        } catch (RiftException e) {
            if (mode == VersionCheck.WARN) {
                LOG.log(Level.WARNING, "unable to verify the rift engine version: " + e.getMessage());
                return;
            }
            throw e;
        }
        switch (decide(version, mode, abiVerified)) {
            case PASS -> { }
            case WARN -> {
                if (abiVerified) {
                    LOG.log(Level.WARNING, "rift engine reports version " + version + " (below the "
                            + MIN_ENGINE_VERSION + " floor) but its C-ABI is v2-complete — trusting the ABI "
                            + "(this is normal for a locally-built engine reporting a placeholder version).");
                } else {
                    LOG.log(Level.WARNING, "rift-java requires rift >= " + MIN_ENGINE_VERSION + ", found " + version);
                }
            }
            case FAIL -> throw new EngineUnavailable("rift-java requires rift >= " + MIN_ENGINE_VERSION
                    + ", found " + version + ". If you know the engine is compatible, relax the check via "
                    + "EmbeddedOptions/ConnectOptions.versionCheck(WARN|OFF) or -Drift.versionCheck=warn|off.");
        }
    }

    private static String extractVersion(JsonValue config) {
        if (config instanceof JsonObject obj && obj.get("version") instanceof JsonString s) {
            return s.value();
        }
        throw new CommunicationError("rift admin API GET /config response is missing a 'version' field");
    }

    /** Compares major.minor.patch, ignoring any {@code -pre} suffix on the patch component. */
    private static int compareSemver(String a, String b) {
        int[] pa = parseSemver(a);
        int[] pb = parseSemver(b);
        for (int i = 0; i < 3; i++) {
            int cmp = Integer.compare(pa[i], pb[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static int[] parseSemver(String version) {
        String v = version.isEmpty() ? version
                : (version.charAt(0) == 'v' || version.charAt(0) == 'V') ? version.substring(1) : version;
        String[] parts = v.split("\\.", 3);
        int[] out = new int[3];
        for (int i = 0; i < parts.length && i < 3; i++) {
            out[i] = leadingInt(parts[i]);
        }
        return out;
    }

    private static int leadingInt(String s) {
        int end = 0;
        while (end < s.length() && Character.isDigit(s.charAt(end))) {
            end++;
        }
        return end == 0 ? 0 : Integer.parseInt(s.substring(0, end));
    }

    @Override
    public Imposter create(ImposterSpec spec) {
        return create(spec.build());
    }

    @Override
    public Imposter create(ImposterDefinition def) {
        return create(JsonValue.parse(def.toJson()));
    }

    @Override
    public Imposter create(JsonValue json) {
        JsonValue created = transport.createImposter(json);
        return new ImposterImpl(extractPort(created), transport, options);
    }

    @Override
    public Imposter create(String json) {
        return create(JsonValue.parse(json));
    }

    @Override
    public Optional<Imposter> imposter(int port) {
        try {
            transport.getImposter(port);
            return Optional.of(new ImposterImpl(port, transport, options));
        } catch (ImposterNotFound e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Imposter> imposters() {
        JsonValue result = transport.listImposters(false, false);
        List<Imposter> out = new ArrayList<>();
        if (result instanceof JsonObject obj && obj.get("imposters") instanceof JsonArray arr) {
            for (JsonValue v : arr.items()) {
                out.add(new ImposterImpl(extractPort(v), transport, options));
            }
        }
        return List.copyOf(out);
    }

    @Override
    public void deleteAll() {
        transport.deleteAll();
    }

    @Override
    public ApplyResult applyConfig(JsonValue config) {
        return ApplyResult.read(transport.applyConfig(config));
    }

    @Override
    public void replaceAll(List<ImposterDefinition> imposters) {
        JsonArray docs = new JsonArray(imposters.stream().map(d -> (JsonValue) JsonValue.parse(d.toJson())).toList());
        JsonObject doc = JsonObject.builder().put("imposters", docs).build();
        transport.replaceAllImposters(doc);
    }

    @Override
    public EngineInfo info() {
        return EngineInfo.read(transport.buildInfo());
    }

    @Override
    public URI adminUri() {
        return transport.adminUri();
    }

    @Override
    public Intercept intercept(InterceptOptions options) {
        // Checked (and flipped) before touching the transport at all: a rejected second call must
        // never start a second listener even transiently.
        if (!interceptStarted.compareAndSet(false, true)) {
            throw new IllegalStateException("intercept already started for this engine");
        }
        try {
            if (options.isAttach()) {
                // No listener to start: probe the already-running one (started at engine launch via
                // --intercept-port), then bind to the given endpoint.
                transport.interceptListRules();
                return new InterceptImpl(transport, options.host(), options.port());
            }
            JsonValue response = transport.startIntercept(options.toJson());
            return new InterceptImpl(transport, response);
        } catch (RuntimeException e) {
            // The listener didn't actually start — reset so a genuine failure is retryable, while a
            // concurrent/second call was still blocked by the CAS above.
            interceptStarted.set(false);
            throw e;
        }
    }

    @Override
    public RiftAsync async() {
        return new RiftAsyncImpl(this);
    }

    @Override
    public void close() {
        // onClose (which stops a spawned process) must run even if the transport close ever throws,
        // so the managed engine is never left running past the Rift handle that owns it.
        try {
            transport.close();
        } finally {
            onClose.run();
        }
    }

    private static int extractPort(JsonValue value) {
        if (value instanceof JsonObject obj && obj.get("port") instanceof JsonNumber n) {
            return n.asInt();
        }
        throw new CommunicationError("rift admin API response is missing a 'port' field");
    }
}
