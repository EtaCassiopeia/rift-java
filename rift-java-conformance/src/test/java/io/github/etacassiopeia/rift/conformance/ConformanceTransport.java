package io.github.etacassiopeia.rift.conformance;

import io.github.etacassiopeia.rift.Rift;
import io.github.etacassiopeia.rift.SpawnOptions;
import io.github.etacassiopeia.rift.json.JsonValue;

import java.util.Locale;

/**
 * The transport axis of the conformance run. The same corpus and the same four gates run over each
 * transport the SDK supports; a transport is one parameter, selected by {@code $CONFORMANCE_TRANSPORT}
 * (default {@link #SPAWN}).
 */
enum ConformanceTransport {

    /** The engine as a spawned process; relative {@code data/} paths resolve via the working directory. */
    SPAWN {
        @Override
        boolean isAvailable() {
            return true;
        }

        @Override
        Rift engine(Corpus corpus) {
            return Rift.spawn(SpawnOptions.builder()
                    .version(corpus.engineVersion())
                    .workingDir(corpus.payloadRoot())
                    .allowInjection(true)
                    .build());
        }

        @Override
        String prepareFixture(String imposterJson, Corpus corpus) {
            return imposterJson;
        }
    },

    /** The engine in-process over Panama FFM; relative {@code data/} paths must be absolutized. */
    EMBEDDED {
        @Override
        boolean isAvailable() {
            return Rift.isEmbeddedAvailable();
        }

        @Override
        Rift engine(Corpus corpus) {
            return Rift.embedded();
        }

        @Override
        String prepareFixture(String imposterJson, Corpus corpus) {
            return Corpus.rewriteForEmbedded(JsonValue.parse(imposterJson), corpus.payloadRoot()).toJson();
        }
    };

    /** True when this transport can run in the current lane (JDK, native library, classpath). */
    abstract boolean isAvailable();

    /** Starts the engine for this transport, ready to serve the corpus. */
    abstract Rift engine(Corpus corpus);

    /** Adapts a fixture (raw or DSL-built) to this transport before it is created on the engine. */
    abstract String prepareFixture(String imposterJson, Corpus corpus);

    /** The transport selected by {@code -Dconformance.transport} / {@code $CONFORMANCE_TRANSPORT}, default SPAWN. */
    static ConformanceTransport selected() {
        return resolve(System.getProperty("conformance.transport"), System.getenv("CONFORMANCE_TRANSPORT"));
    }

    /**
     * Resolves the transport from the system property (higher precedence) then the environment
     * variable, defaulting to SPAWN when both are absent/blank. An unrecognized value is a hard error
     * (never a silent fall-back to SPAWN, which would mask a misconfigured embedded lane).
     */
    static ConformanceTransport resolve(String property, String env) {
        String value = (property != null && !property.isBlank()) ? property
                : (env != null && !env.isBlank()) ? env : null;
        if (value == null) {
            return SPAWN;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "SPAWN" -> SPAWN;
            case "EMBEDDED" -> EMBEDDED;
            default -> throw new IllegalArgumentException(
                    "unknown CONFORMANCE_TRANSPORT '" + value + "' (expected SPAWN or EMBEDDED)");
        };
    }
}
