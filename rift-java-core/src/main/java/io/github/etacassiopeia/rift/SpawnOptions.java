package io.github.etacassiopeia.rift;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable configuration for {@link Rift#spawn(SpawnOptions)}: which {@code rift} binary to run
 * (or how to resolve/download one), how to launch it, and how long to wait for it to become
 * healthy or to shut down.
 */
public final class SpawnOptions {

    private final Optional<Path> binaryPath;
    private final String version;
    private final String host;
    private final int adminPort;
    private final boolean allowInjection;
    private final boolean localOnly;
    private final String logLevel;
    private final Map<String, String> env;
    private final Optional<Path> workingDir;
    private final Optional<URI> mirrorUrl;
    private final Duration startupTimeout;
    private final Duration shutdownTimeout;
    private final boolean inheritLog;

    private SpawnOptions(Builder b) {
        this.binaryPath = b.binaryPath;
        this.version = b.version;
        this.host = b.host;
        this.adminPort = b.adminPort;
        this.allowInjection = b.allowInjection;
        this.localOnly = b.localOnly;
        this.logLevel = b.logLevel;
        this.env = Map.copyOf(b.env);
        this.workingDir = b.workingDir;
        this.mirrorUrl = b.mirrorUrl;
        this.startupTimeout = b.startupTimeout;
        this.shutdownTimeout = b.shutdownTimeout;
        this.inheritLog = b.inheritLog;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Path> binaryPath() {
        return binaryPath;
    }

    public String version() {
        return version;
    }

    public String host() {
        return host;
    }

    public int adminPort() {
        return adminPort;
    }

    public boolean allowInjection() {
        return allowInjection;
    }

    public boolean localOnly() {
        return localOnly;
    }

    public String logLevel() {
        return logLevel;
    }

    public Map<String, String> env() {
        return env;
    }

    public Optional<Path> workingDir() {
        return workingDir;
    }

    public Optional<URI> mirrorUrl() {
        return mirrorUrl;
    }

    public Duration startupTimeout() {
        return startupTimeout;
    }

    public Duration shutdownTimeout() {
        return shutdownTimeout;
    }

    public boolean inheritLog() {
        return inheritLog;
    }

    public static final class Builder {

        private Optional<Path> binaryPath = Optional.empty();
        private String version = RiftImpl.MIN_ENGINE_VERSION;
        private String host = "127.0.0.1";
        private int adminPort = 0;
        private boolean allowInjection = true;
        private boolean localOnly = true;
        private String logLevel = "info";
        private final Map<String, String> env = new LinkedHashMap<>();
        private Optional<Path> workingDir = Optional.empty();
        private Optional<URI> mirrorUrl = Optional.empty();
        private Duration startupTimeout = Duration.ofSeconds(15);
        private Duration shutdownTimeout = Duration.ofSeconds(5);
        private boolean inheritLog = false;

        private Builder() {
        }

        public Builder binaryPath(Path binaryPath) {
            this.binaryPath = Optional.of(Objects.requireNonNull(binaryPath, "binaryPath"));
            return this;
        }

        public Builder version(String version) {
            this.version = Objects.requireNonNull(version, "version");
            return this;
        }

        public Builder host(String host) {
            this.host = Objects.requireNonNull(host, "host");
            return this;
        }

        public Builder adminPort(int adminPort) {
            this.adminPort = adminPort;
            return this;
        }

        public Builder allowInjection(boolean allowInjection) {
            this.allowInjection = allowInjection;
            return this;
        }

        public Builder localOnly(boolean localOnly) {
            this.localOnly = localOnly;
            return this;
        }

        public Builder logLevel(String logLevel) {
            this.logLevel = Objects.requireNonNull(logLevel, "logLevel");
            return this;
        }

        public Builder env(Map<String, String> env) {
            Objects.requireNonNull(env, "env");
            this.env.clear();
            this.env.putAll(env);
            return this;
        }

        public Builder workingDir(Path workingDir) {
            this.workingDir = Optional.of(Objects.requireNonNull(workingDir, "workingDir"));
            return this;
        }

        public Builder mirrorUrl(URI mirrorUrl) {
            this.mirrorUrl = Optional.of(Objects.requireNonNull(mirrorUrl, "mirrorUrl"));
            return this;
        }

        public Builder startupTimeout(Duration startupTimeout) {
            this.startupTimeout = Objects.requireNonNull(startupTimeout, "startupTimeout");
            return this;
        }

        public Builder shutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
            return this;
        }

        public Builder inheritLog(boolean inheritLog) {
            this.inheritLog = inheritLog;
            return this;
        }

        public SpawnOptions build() {
            return new SpawnOptions(this);
        }
    }
}
