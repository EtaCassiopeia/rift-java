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

final class RiftImpl implements Rift {

    /** The oldest engine version this SDK is known to work against; see the version preflight. */
    static final String MIN_ENGINE_VERSION = "0.12.0";

    private static final System.Logger LOG = System.getLogger(RiftImpl.class.getName());

    private final RiftTransport transport;
    private final ConnectOptions options;
    private final Runnable onClose;

    private RiftImpl(RiftTransport transport, ConnectOptions options, Runnable onClose) {
        this.transport = transport;
        this.options = options;
        this.onClose = onClose;
    }

    static Rift connect(ConnectOptions options) {
        RiftTransport transport = new RemoteTransport(options.adminUri(), options.apiKey(), options.requestTimeout());
        if (options.versionCheck() != VersionCheck.OFF) {
            preflight(transport, options.versionCheck());
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

    private static void preflight(RiftTransport transport, VersionCheck mode) {
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
        if (compareSemver(version, MIN_ENGINE_VERSION) < 0) {
            String message = "rift-java requires rift >= " + MIN_ENGINE_VERSION + ", found " + version;
            if (mode == VersionCheck.FAIL) {
                throw new EngineUnavailable(message);
            }
            LOG.log(Level.WARNING, message);
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
