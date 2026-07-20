package io.github.achirdlabs.rift;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable configuration for {@link Rift#embedded(EmbeddedOptions)}: where to find the
 * {@code librift_ffi} native library and how the in-process engine's admin surface (used for the
 * operations with no direct C-ABI entry point, and for imposters' own reported {@code uri()})
 * should be addressed.
 */
public final class EmbeddedOptions {

    private final Optional<Path> libraryPath;
    private final VersionCheck versionCheck;
    private final boolean serveAdminEagerly;
    private final String adminHost;
    private final int adminPort;
    private final Optional<String> apiKey;

    private EmbeddedOptions(
            Optional<Path> libraryPath,
            VersionCheck versionCheck,
            boolean serveAdminEagerly,
            String adminHost,
            int adminPort,
            Optional<String> apiKey) {
        this.libraryPath = libraryPath;
        this.versionCheck = versionCheck;
        this.serveAdminEagerly = serveAdminEagerly;
        this.adminHost = adminHost;
        this.adminPort = adminPort;
        this.apiKey = apiKey;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** An explicit path to the {@code librift_ffi} native library, bypassing classpath/env/property resolution. */
    public Optional<Path> libraryPath() {
        return libraryPath;
    }

    public VersionCheck versionCheck() {
        return versionCheck;
    }

    /** Whether the in-process admin server should be started immediately rather than on first need. */
    public boolean serveAdminEagerly() {
        return serveAdminEagerly;
    }

    public String adminHost() {
        return adminHost;
    }

    public int adminPort() {
        return adminPort;
    }

    public Optional<String> apiKey() {
        return apiKey;
    }

    public static final class Builder {

        private Optional<Path> libraryPath = Optional.empty();
        private VersionCheck versionCheck = VersionCheck.resolveDefault();
        private boolean serveAdminEagerly = false;
        private String adminHost = "127.0.0.1";
        private int adminPort = 0;
        private Optional<String> apiKey = Optional.empty();

        private Builder() {
        }

        public Builder libraryPath(Path libraryPath) {
            this.libraryPath = Optional.of(Objects.requireNonNull(libraryPath, "libraryPath"));
            return this;
        }

        public Builder versionCheck(VersionCheck versionCheck) {
            this.versionCheck = Objects.requireNonNull(versionCheck, "versionCheck");
            return this;
        }

        public Builder serveAdminEagerly(boolean serveAdminEagerly) {
            this.serveAdminEagerly = serveAdminEagerly;
            return this;
        }

        public Builder adminHost(String adminHost) {
            this.adminHost = Objects.requireNonNull(adminHost, "adminHost");
            return this;
        }

        public Builder adminPort(int adminPort) {
            this.adminPort = adminPort;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = Optional.of(apiKey);
            return this;
        }

        public EmbeddedOptions build() {
            return new EmbeddedOptions(libraryPath, versionCheck, serveAdminEagerly, adminHost, adminPort, apiKey);
        }
    }
}
