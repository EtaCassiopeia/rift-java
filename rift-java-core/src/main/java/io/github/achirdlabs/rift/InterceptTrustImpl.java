package io.github.achirdlabs.rift;

import io.github.achirdlabs.rift.error.CommunicationError;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * Pure-Java {@link InterceptTrust}: every operation works directly off the CA PEM text, with no
 * engine call and no temp files — so this is usable from any transport (embedded, remote, spawned
 * alike) purely as a function of the PEM {@link Intercept#trust()} already fetched. The
 * {@code *WithSystemCAs} variants additionally fold in the JVM's default trust anchors.
 */
final class InterceptTrustImpl implements InterceptTrust {

    private static final String DEFAULT_TRUSTSTORE_PASSWORD = "changeit";
    private static final String CA_ALIAS = "intercept-ca";

    private final String caPem;

    InterceptTrustImpl(String caPem) {
        this.caPem = caPem;
    }

    @Override
    public String caPem() {
        return caPem;
    }

    @Override
    public SSLContext sslContext() {
        return sslContext(false);
    }

    @Override
    public SSLContext sslContextWithSystemCAs() {
        return sslContext(true);
    }

    private SSLContext sslContext(boolean includeSystemCAs) {
        try {
            KeyStore ks = buildTrustStore(KeyStore.getDefaultType(), includeSystemCAs);
            TrustManagerFactory tmf;
            try {
                tmf = TrustManagerFactory.getInstance("PKIX");
            } catch (GeneralSecurityException noPkix) {
                tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            }
            tmf.init(ks);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx;
        } catch (GeneralSecurityException | IOException e) {
            throw new CommunicationError("failed to build an SSLContext from the intercept CA PEM", e);
        }
    }

    @Override
    public void exportTruststore(TruststoreFormat format, String password, Path out) {
        exportTruststore(format, password, out, false);
    }

    @Override
    public void exportTruststoreWithSystemCAs(TruststoreFormat format, String password, Path out) {
        exportTruststore(format, password, out, true);
    }

    private void exportTruststore(TruststoreFormat format, String password, Path out, boolean includeSystemCAs) {
        String effectivePassword = password != null ? password : DEFAULT_TRUSTSTORE_PASSWORD;
        try {
            KeyStore ks = buildTrustStore(format.name(), includeSystemCAs);
            try (OutputStream out2 = Files.newOutputStream(out)) {
                ks.store(out2, effectivePassword.toCharArray());
            }
        } catch (GeneralSecurityException | IOException e) {
            throw new CommunicationError("failed to export a " + format + " truststore for the intercept CA", e);
        }
    }

    /** A fresh {@code storeType} keystore holding the intercept CA, plus the JVM default anchors when asked. */
    private KeyStore buildTrustStore(String storeType, boolean includeSystemCAs)
            throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance(storeType);
        ks.load(null, null);
        if (includeSystemCAs) {
            int i = 0;
            for (X509Certificate anchor : systemTrustAnchors()) {
                ks.setCertificateEntry("system-ca-" + (i++), anchor);
            }
        }
        ks.setCertificateEntry(CA_ALIAS, parseCertificate());
        return ks;
    }

    /** The JVM's default trust anchors ({@code cacerts}), via the platform default trust manager. */
    private static X509Certificate[] systemTrustAnchors() throws GeneralSecurityException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null); // null → load the JVM's default cacerts
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager x509) {
                return x509.getAcceptedIssuers();
            }
        }
        return new X509Certificate[0];
    }

    private Certificate parseCertificate() throws GeneralSecurityException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return factory.generateCertificate(new ByteArrayInputStream(caPem.getBytes(StandardCharsets.UTF_8)));
    }
}
