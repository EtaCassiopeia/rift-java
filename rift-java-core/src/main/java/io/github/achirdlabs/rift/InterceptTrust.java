package io.github.achirdlabs.rift;

import javax.net.ssl.SSLContext;
import java.nio.file.Path;

/**
 * Trust material for an intercept's CA, derived purely from its PEM text — no engine call, no
 * temp files. Lets a client trust the intercept's TLS-MITM certificates, either in-memory (via
 * {@link #sslContext()}, e.g. for {@code java.net.http.HttpClient}) or as a truststore file (via
 * {@link #exportTruststore}, e.g. for a JVM {@code -Djavax.net.ssl.trustStore}).
 *
 * <p>The plain {@code sslContext()}/{@code exportTruststore(...)} trust <em>only</em> the intercept
 * CA — right for a fully hermetic SUT. When the SUT also makes real HTTPS calls (a JVM whose whole
 * truststore is replaced via {@code -Djavax.net.ssl.trustStore}, so it must still reach real
 * endpoints), use the {@code *WithSystemCAs} variants, which additionally trust the JVM's default
 * trust anchors.
 */
public interface InterceptTrust {

    /** The intercept CA certificate, PEM-encoded. */
    String caPem();

    /** An {@link SSLContext} whose trust manager trusts only the intercept CA, built entirely in memory. */
    SSLContext sslContext();

    /**
     * An {@link SSLContext} trusting the intercept CA <em>and</em> the JVM's default trust anchors
     * (its {@code cacerts}) — for a SUT that must reach both intercepted and real HTTPS hosts.
     */
    SSLContext sslContextWithSystemCAs();

    /** Writes a {@code format} truststore containing the intercept CA to {@code out}, protected by {@code password} ({@code null} defaults to {@code "changeit"}). */
    void exportTruststore(TruststoreFormat format, String password, Path out);

    /**
     * Writes a {@code format} truststore containing the intercept CA <em>and</em> the JVM's default
     * trust anchors to {@code out} — the file to hand a SUT that replaces its whole truststore yet
     * still calls real HTTPS endpoints. {@code null} password defaults to {@code "changeit"}.
     */
    void exportTruststoreWithSystemCAs(TruststoreFormat format, String password, Path out);
}
