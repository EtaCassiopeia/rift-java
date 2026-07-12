package io.github.etacassiopeia.rift;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InterceptTrust} builds an in-memory {@link SSLContext} trusting the intercept CA and exports
 * PKCS12/JKS truststores — all in pure Java from the CA PEM, so it is transport-agnostic and needs no
 * engine. Uses a committed self-signed test CA (CN=rift-test-intercept-CA).
 */
class InterceptTrustTest {

    private static String caPem;

    @BeforeAll
    static void loadCa() throws Exception {
        try (InputStream in = InterceptTrustTest.class.getResourceAsStream("/test-intercept-ca.pem")) {
            caPem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void caPemRoundTrips() {
        assertEquals(caPem, new InterceptTrustImpl(caPem).caPem());
    }

    @Test
    void sslContextIsBuiltInMemory() throws Exception {
        SSLContext ctx = new InterceptTrustImpl(caPem).sslContext();
        assertNotNull(ctx);
        assertNotNull(ctx.getSocketFactory(), "an initialized SSLContext");
    }

    @Test
    void exportsAPkcs12TruststoreContainingTheCa(@TempDir Path dir) throws Exception {
        Path ks = dir.resolve("trust.p12");
        new InterceptTrustImpl(caPem).exportTruststore(TruststoreFormat.PKCS12, "changeit", ks);
        assertTrue(containsTestCa(ks, "PKCS12", "changeit"));
    }

    @Test
    void exportsAJksTruststoreContainingTheCa(@TempDir Path dir) throws Exception {
        Path ks = dir.resolve("trust.jks");
        new InterceptTrustImpl(caPem).exportTruststore(TruststoreFormat.JKS, "secret", ks);
        assertTrue(containsTestCa(ks, "JKS", "secret"));
    }

    @Test
    void nullPasswordDefaultsToChangeit(@TempDir Path dir) throws Exception {
        Path ks = dir.resolve("default.p12");
        new InterceptTrustImpl(caPem).exportTruststore(TruststoreFormat.PKCS12, null, ks);
        assertTrue(containsTestCa(ks, "PKCS12", "changeit"), "null password → \"changeit\"");
    }

    @Test
    void sslContextWithSystemCAsIsInitialized() throws Exception {
        SSLContext ctx = new InterceptTrustImpl(caPem).sslContextWithSystemCAs();
        assertNotNull(ctx);
        assertNotNull(ctx.getSocketFactory(), "an initialized SSLContext");
    }

    @Test
    void exportWithSystemCAsContainsTheCaPlusTheSystemAnchors(@TempDir Path dir) throws Exception {
        Path caOnly = dir.resolve("ca-only.p12");
        Path withSystem = dir.resolve("with-system.p12");
        InterceptTrustImpl trust = new InterceptTrustImpl(caPem);
        trust.exportTruststore(TruststoreFormat.PKCS12, "changeit", caOnly);
        trust.exportTruststoreWithSystemCAs(TruststoreFormat.PKCS12, "changeit", withSystem);

        // The intercept CA is present in both...
        assertTrue(containsTestCa(withSystem, "PKCS12", "changeit"), "intercept CA must be present");
        // ...and the with-system store additionally carries the JVM default anchors (many more entries).
        int caOnlyCount = certCount(caOnly, "PKCS12", "changeit");
        int withSystemCount = certCount(withSystem, "PKCS12", "changeit");
        assertEquals(1, caOnlyCount, "CA-only store holds exactly the intercept CA");
        assertTrue(withSystemCount > caOnlyCount + 1,
                "with-system store must fold in the JVM trust anchors, got " + withSystemCount);
    }

    private static int certCount(Path store, String type, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance(type);
        try (InputStream in = Files.newInputStream(store)) {
            ks.load(in, password.toCharArray());
        }
        return ks.size();
    }

    private static boolean containsTestCa(Path store, String type, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance(type);
        try (InputStream in = Files.newInputStream(store)) {
            ks.load(in, password.toCharArray());
        }
        for (String alias : Collections.list(ks.aliases())) {
            if (ks.getCertificate(alias) instanceof X509Certificate cert
                    && cert.getSubjectX500Principal().getName().contains("rift-test-intercept-CA")) {
                return true;
            }
        }
        return false;
    }
}
