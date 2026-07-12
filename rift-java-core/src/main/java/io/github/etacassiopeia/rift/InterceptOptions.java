package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.json.JsonNull;
import io.github.etacassiopeia.rift.json.JsonNumber;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Set;

/**
 * Options for {@link Rift#intercept(InterceptOptions)}: the bind address for the intercept
 * listener, and an optional committed CA (both a cert and a key, or neither — a half-supplied pair
 * is rejected at {@link Builder#ca} rather than surfacing as a confusing engine-side error). With no
 * CA given, the engine mints a fresh ephemeral one per listener.
 *
 * <p>A CA may be supplied as file paths ({@link Builder#ca(Path, Path)}) or <em>in memory</em> — a
 * PEM {@link Builder#ca(String, String) String}, {@link Builder#ca(byte[], byte[]) byte[]}, or a
 * {@link Builder#ca(KeyStore, char[]) KeyStore} (e.g. from a secret store, without touching disk in
 * caller code). In-memory material is written to a private, owner-only temp file the SDK owns
 * ({@code deleteOnExit}); the engine still loads it from that path, so — like a file-path CA — it
 * must be on a filesystem the engine can read (trivially true for the embedded transport; a
 * containerized remote engine needs the path mounted, or engine-side inline-bytes support, tracked
 * upstream).
 */
public final class InterceptOptions {

    private final String host;
    private final int port;
    private final Path caCertPath;
    private final Path caKeyPath;
    private final boolean attach;

    private InterceptOptions(String host, int port, Path caCertPath, Path caKeyPath, boolean attach) {
        this.host = host;
        this.port = port;
        this.caCertPath = caCertPath;
        this.caKeyPath = caKeyPath;
        this.attach = attach;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Attach to an intercept listener the engine started at launch (via {@code --intercept-port} /
     * {@code RIFT_INTERCEPT_PORT}) at {@code host:port}, rather than starting one. This is how a
     * remote/connected engine — whose admin API can only <em>manage</em> a listener, not start one —
     * exposes intercept; {@code host}/{@code port} are the reachable endpoint (e.g. a mapped Docker port).
     */
    public static InterceptOptions attach(String host, int port) {
        Objects.requireNonNull(host, "host");
        return new InterceptOptions(host, port, null, null, true);
    }

    /** Whether these options attach to an already-running listener rather than starting one. */
    boolean isAttach() {
        return attach;
    }

    String host() {
        return host;
    }

    int port() {
        return port;
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
            Objects.requireNonNull(host, "host");
            // The engine requires the intercept BIND host to be an IP literal (a hostname is rejected
            // engine-side with a confusing error); validate client-side with a clear message.
            if (!isIpLiteral(host)) {
                throw new IllegalArgumentException(
                        "intercept bind host must be an IP literal (e.g. 127.0.0.1 or 0.0.0.0), not '" + host + "'");
            }
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Loads the intercept CA from {@code certPem}/{@code keyPem} files (both required, or omit
         * both to keep the default ephemeral CA) rather than minting a fresh one per listener —
         * letting independent engine instances share one trust anchor.
         */
        public Builder ca(Path certPem, Path keyPem) {
            requireBothOrNeither(certPem, keyPem);
            this.caCertPath = certPem;
            this.caKeyPath = keyPem;
            return this;
        }

        /** Loads the intercept CA from in-memory PEM text (both required, or omit both). */
        public Builder ca(String certPem, String keyPem) {
            requireBothOrNeither(certPem, keyPem);
            if (certPem == null) {
                this.caCertPath = null;
                this.caKeyPath = null;
                return this;
            }
            this.caCertPath = writeTemp("rift-intercept-ca-cert", certPem);
            this.caKeyPath = writeTemp("rift-intercept-ca-key", keyPem);
            return this;
        }

        /** Loads the intercept CA from in-memory PEM bytes (both required, or omit both). */
        public Builder ca(byte[] certPem, byte[] keyPem) {
            requireBothOrNeither(certPem, keyPem);
            return ca(certPem == null ? null : new String(certPem, StandardCharsets.UTF_8),
                    keyPem == null ? null : new String(keyPem, StandardCharsets.UTF_8));
        }

        /**
         * Loads the intercept CA from a {@code keyStore}'s first private-key entry (the cert and key
         * are PEM-encoded internally). {@code password} unlocks the key entry.
         */
        public Builder ca(KeyStore keyStore, char[] password) {
            Objects.requireNonNull(keyStore, "keyStore");
            String[] pem = extractPem(keyStore, password);
            return ca(pem[0], pem[1]);
        }

        public InterceptOptions build() {
            return new InterceptOptions(host, port, caCertPath, caKeyPath, false);
        }

        /** An IPv4 literal (dotted quad, each octet 0-255) or an IPv6 literal (contains a colon). */
        private static boolean isIpLiteral(String host) {
            if (host.indexOf(':') >= 0) {
                return true; // IPv6 literal (possibly bracketed)
            }
            String[] octets = host.split("\\.", -1);
            if (octets.length != 4) {
                return false;
            }
            for (String octet : octets) {
                if (octet.isEmpty() || octet.length() > 3) {
                    return false;
                }
                for (int i = 0; i < octet.length(); i++) {
                    if (!Character.isDigit(octet.charAt(i))) {
                        return false;
                    }
                }
                if (Integer.parseInt(octet) > 255) {
                    return false;
                }
            }
            return true;
        }

        private static void requireBothOrNeither(Object cert, Object key) {
            if ((cert == null) != (key == null)) {
                throw new IllegalArgumentException(
                        "InterceptOptions.ca(cert, key) requires both a cert and a key, or neither");
            }
        }

        private static Path writeTemp(String prefix, String pem) {
            try {
                Path file;
                try {
                    file = Files.createTempFile(prefix, ".pem", PosixFilePermissions.asFileAttribute(
                            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
                } catch (UnsupportedOperationException nonPosix) {
                    file = Files.createTempFile(prefix, ".pem"); // Windows etc. — no POSIX perms
                }
                file.toFile().deleteOnExit();
                Files.writeString(file, pem, StandardCharsets.UTF_8);
                return file;
            } catch (IOException e) {
                throw new UncheckedIOException("failed to stage the in-memory intercept CA to a temp file", e);
            }
        }

        private static String[] extractPem(KeyStore keyStore, char[] password) {
            try {
                Enumeration<String> aliases = keyStore.aliases();
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    if (keyStore.isKeyEntry(alias)) {
                        Key key = keyStore.getKey(alias, password);
                        Certificate cert = keyStore.getCertificate(alias);
                        if (key != null && cert != null) {
                            return new String[] {
                                    pem("CERTIFICATE", cert.getEncoded()), pem("PRIVATE KEY", key.getEncoded())};
                        }
                    }
                }
                throw new IllegalArgumentException("KeyStore has no private-key entry with a certificate");
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException("failed to read the CA from the KeyStore: " + e.getMessage(), e);
            }
        }

        private static String pem(String type, byte[] der) {
            String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(der);
            return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
        }
    }
}
