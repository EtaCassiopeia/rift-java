package io.github.etacassiopeia.rift;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * The rift-java SDK version, resolved at build time from a filtered resource.
 *
 * <p>This is the SDK's own version, distinct from the Rift engine version reported by a running
 * engine (see the version preflight in the remote and embedded transports).
 */
public final class RiftVersion {

    private static final String VERSION = load();

    private RiftVersion() {
    }

    /**
     * {@return the rift-java SDK version, e.g. {@code "0.1.0-SNAPSHOT"}}
     */
    public static String get() {
        return VERSION;
    }

    private static String load() {
        try (InputStream in = RiftVersion.class.getResourceAsStream("/rift-version.properties")) {
            if (in == null) {
                throw new IllegalStateException("rift-version.properties missing from the rift-java-core jar");
            }
            Properties props = new Properties();
            props.load(in);
            String version = props.getProperty("version");
            if (version == null || version.isBlank()) {
                throw new IllegalStateException("version property absent from rift-version.properties");
            }
            return version;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read rift-version.properties", e);
        }
    }
}
