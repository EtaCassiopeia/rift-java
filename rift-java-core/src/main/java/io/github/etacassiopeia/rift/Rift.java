package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.dsl.ImposterSpec;
import io.github.etacassiopeia.rift.error.EngineUnavailable;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.ImposterDefinition;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * A client for a running rift engine's admin API. {@link #connect(URI)}/{@link
 * #connect(ConnectOptions)} is the only transport implemented so far — remote HTTP against an
 * already-running engine. {@link #spawn()} (issue #5) and {@link #embedded()} (issue #10,
 * requires the {@code rift-java-embedded} module) are reserved but not yet implemented.
 */
public interface Rift extends AutoCloseable {

    static Rift connect(URI adminUri) {
        return connect(ConnectOptions.builder(adminUri).build());
    }

    static Rift connect(ConnectOptions options) {
        return RiftImpl.connect(options);
    }

    static Rift spawn() {
        throw new UnsupportedOperationException("spawn transport arrives in issue #5");
    }

    static Rift spawn(SpawnOptions options) {
        throw new UnsupportedOperationException("spawn transport arrives in issue #5");
    }

    static Rift embedded() {
        throw new EngineUnavailable("embedded transport requires rift-java-embedded (issue #10)");
    }

    static Rift embedded(EmbeddedOptions options) {
        throw new EngineUnavailable("embedded transport requires rift-java-embedded (issue #10)");
    }

    static boolean isEmbeddedAvailable() {
        return false;
    }

    Imposter create(ImposterSpec spec);

    Imposter create(ImposterDefinition def);

    Imposter create(JsonValue json);

    Imposter create(String json);

    /** The imposter bound to {@code port}, or {@link Optional#empty()} if none is. */
    Optional<Imposter> imposter(int port);

    List<Imposter> imposters();

    void deleteAll();

    ApplyResult applyConfig(JsonValue config);

    void replaceAll(List<ImposterDefinition> imposters);

    EngineInfo info();

    URI adminUri();

    RiftAsync async();

    @Override
    void close();
}
