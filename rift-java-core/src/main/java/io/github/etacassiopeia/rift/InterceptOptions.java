package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.json.JsonNull;
import io.github.etacassiopeia.rift.json.JsonNumber;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Options for {@link Rift#intercept(InterceptOptions)}: the bind address for the intercept
 * listener, and an optional committed CA (both a cert and a key PEM, or neither — a half-supplied
 * pair is rejected at {@link Builder#ca} rather than surfacing as a confusing engine-side error).
 * With no CA given, the engine mints a fresh ephemeral one per listener.
 */
public final class InterceptOptions {

    private final String host;
    private final int port;
    private final Path caCertPath;
    private final Path caKeyPath;

    private InterceptOptions(String host, int port, Path caCertPath, Path caKeyPath) {
        this.host = host;
        this.port = port;
        this.caCertPath = caCertPath;
        this.caKeyPath = caKeyPath;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The {@code {host,port,caCertPath,caKeyPath}} JSON the engine's {@code rift_start_intercept}/{@code POST /intercept} expects. */
    public JsonValue toJson() {
        return JsonObject.builder()
                .put("host", new JsonString(host))
                .put("port", JsonNumber.of(port))
                .put("caCertPath", caCertPath == null ? JsonNull.INSTANCE : new JsonString(caCertPath.toString()))
                .put("caKeyPath", caKeyPath == null ? JsonNull.INSTANCE : new JsonString(caKeyPath.toString()))
                .build();
    }

    public static final class Builder {
        private String host = "127.0.0.1";
        private int port = 0;
        private Path caCertPath;
        private Path caKeyPath;

        private Builder() {
        }

        public Builder host(String host) {
            this.host = Objects.requireNonNull(host, "host");
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Loads the intercept CA from {@code certPem}/{@code keyPem} (both required, or omit both
         * to keep the default ephemeral CA) rather than minting a fresh one per listener — letting
         * independent engine instances share one trust anchor.
         */
        public Builder ca(Path certPem, Path keyPem) {
            if ((certPem == null) != (keyPem == null)) {
                throw new IllegalArgumentException(
                        "InterceptOptions.ca(certPem, keyPem) requires both paths or neither");
            }
            this.caCertPath = certPem;
            this.caKeyPath = keyPem;
            return this;
        }

        public InterceptOptions build() {
            return new InterceptOptions(host, port, caCertPath, caKeyPath);
        }
    }
}
