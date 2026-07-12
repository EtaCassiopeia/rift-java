package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.KeyFactory;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** In-memory CA input for the intercept: PEM string/bytes/KeyStore materialize to a readable path (issue #82). */
class InterceptOptionsCaTest {

    private static String read(String resource) throws Exception {
        try (InputStream in = InterceptOptionsCaTest.class.getResourceAsStream(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path caPath(InterceptOptions options, String field) {
        JsonValue v = ((JsonObject) options.toJson()).get(field);
        return Path.of(((JsonString) v).value());
    }

    @Test
    void inMemoryStringCaMaterializesToReadableFilesWithTheSameContent() throws Exception {
        String certPem = "-----BEGIN CERTIFICATE-----\nDUMMYCERT\n-----END CERTIFICATE-----\n";
        String keyPem = "-----BEGIN PRIVATE KEY-----\nDUMMYKEY\n-----END PRIVATE KEY-----\n";

        InterceptOptions options = InterceptOptions.builder().ca(certPem, keyPem).build();
        Path certFile = caPath(options, "caCertPath");
        Path keyFile = caPath(options, "caKeyPath");

        assertEquals(certPem, Files.readString(certFile), "cert temp file has the exact PEM");
        assertEquals(keyPem, Files.readString(keyFile), "key temp file has the exact PEM");
    }

    @Test
    void byteArrayCaBehavesLikeString() throws Exception {
        String certPem = "-----BEGIN CERTIFICATE-----\nABC\n-----END CERTIFICATE-----\n";
        String keyPem = "-----BEGIN PRIVATE KEY-----\nDEF\n-----END PRIVATE KEY-----\n";
        InterceptOptions options = InterceptOptions.builder()
                .ca(certPem.getBytes(StandardCharsets.UTF_8), keyPem.getBytes(StandardCharsets.UTF_8)).build();
        assertEquals(certPem, Files.readString(caPath(options, "caCertPath")));
        assertEquals(keyPem, Files.readString(caPath(options, "caKeyPath")));
    }

    @Test
    void halfSuppliedPairIsRejectedForEveryOverload() {
        assertThrows(IllegalArgumentException.class,
                () -> InterceptOptions.builder().ca("cert", (String) null));
        assertThrows(IllegalArgumentException.class,
                () -> InterceptOptions.builder().ca((byte[]) null, "k".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,
                () -> InterceptOptions.builder().ca((Path) null, Path.of("k.pem")));
    }

    @Test
    void keyStoreCaExtractsCertAndKeyPem() throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        Certificate cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(read("/test-inmemory-ca-cert.pem").getBytes(StandardCharsets.UTF_8)));
        PrivateKey key = parseKey(read("/test-inmemory-ca-key.pem"));
        char[] pw = "changeit".toCharArray();
        ks.setKeyEntry("ca", key, pw, new Certificate[] {cert});

        InterceptOptions options = InterceptOptions.builder().ca(ks, pw).build();
        String certOut = Files.readString(caPath(options, "caCertPath"));
        String keyOut = Files.readString(caPath(options, "caKeyPath"));
        assertTrue(certOut.contains("BEGIN CERTIFICATE"), certOut);
        assertTrue(keyOut.contains("BEGIN PRIVATE KEY"), keyOut);
        // The extracted cert PEM parses back to the same certificate.
        Certificate roundTrip = CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(certOut.getBytes(StandardCharsets.UTF_8)));
        assertEquals(cert, roundTrip);
    }

    private static PrivateKey parseKey(String pem) throws Exception {
        String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
