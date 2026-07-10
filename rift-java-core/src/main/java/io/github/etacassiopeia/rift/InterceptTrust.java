package io.github.etacassiopeia.rift;

import javax.net.ssl.SSLContext;
import java.nio.file.Path;

/**
 * Trust material for an intercept's CA, derived purely from its PEM text — no engine call, no
 * temp files. Lets a client trust the intercept's TLS-MITM certificates, either in-memory (via
 * {@link #sslContext()}, e.g. for {@code java.net.http.HttpClient}) or as a truststore file (via
 * {@link #exportTruststore}, e.g. for a JVM {@code -Djavax.net.ssl.trustStore}).
 */
public interface InterceptTrust {

    /** The intercept CA certificate, PEM-encoded. */
    String caPem();

    /** An {@link SSLContext} whose trust manager trusts only the intercept CA, built entirely in memory. */
    SSLContext sslContext();

    /** Writes a {@code format} truststore containing the intercept CA to {@code out}, protected by {@code password} ({@code null} defaults to {@code "changeit"}). */
    void exportTruststore(TruststoreFormat format, String password, Path out);
}
