package io.github.achirdlabs.rift;

import io.github.achirdlabs.rift.json.JsonBool;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonString;
import io.github.achirdlabs.rift.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-memory CA input ships inline in the start body ({@code caCertPem}/{@code caKeyPem}, rift &ge;
 * 0.13.4, #111 building on #82); a file-path CA still ships {@code caCertPath}; {@code generateCa()}
 * requests {@code returnCaKey}.
 */
class InterceptOptionsCaTest {

    private static String read(String resource) throws Exception {
        try (InputStream in = InterceptOptionsCaTest.class.getResourceAsStream(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String field(InterceptOptions options, String name) {
        JsonValue v = ((JsonObject) options.toJson()).get(name);
        return v instanceof JsonString s ? s.value() : null;
    }

    @Test
    void inMemoryStringCaShipsInlinePem() {
        String certPem = "-----BEGIN CERTIFICATE-----\nDUMMYCERT\n-----END CERTIFICATE-----\n";
        String keyPem = "-----BEGIN PRIVATE KEY-----\nDUMMYKEY\n-----END PRIVATE KEY-----\n";
        InterceptOptions options = InterceptOptions.builder().ca(certPem, keyPem).build();
        assertEquals(certPem, field(options, "caCertPem"));
        assertEquals(keyPem, field(options, "caKeyPem"));
        // In-memory input never becomes a path.
        assertFalse(((JsonObject) options.toJson()).has("caCertPath"));
    }

    @Test
    void byteArrayCaBehavesLikeString() {
        String certPem = "-----BEGIN CERTIFICATE-----\nABC\n-----END CERTIFICATE-----\n";
        String keyPem = "-----BEGIN PRIVATE KEY-----\nDEF\n-----END PRIVATE KEY-----\n";
        InterceptOptions options = InterceptOptions.builder()
                .ca(certPem.getBytes(StandardCharsets.UTF_8), keyPem.getBytes(StandardCharsets.UTF_8)).build();
        assertEquals(certPem, field(options, "caCertPem"));
        assertEquals(keyPem, field(options, "caKeyPem"));
    }

    @Test
    void filePathCaShipsPathsNotPem() {
        InterceptOptions options = InterceptOptions.builder().ca(Path.of("ca.pem"), Path.of("key.pem")).build();
        assertEquals("ca.pem", field(options, "caCertPath"));
        assertFalse(((JsonObject) options.toJson()).has("caCertPem"));
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
    void keyStoreCaExtractsCertAndKeyPemInline() throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        Certificate cert = CertificateFactory.getInstance("X.509").generateCertificate(
                new java.io.ByteArrayInputStream(read("/test-inmemory-ca-cert.pem").getBytes(StandardCharsets.UTF_8)));
        PrivateKey key = parseKey(read("/test-inmemory-ca-key.pem"));
        char[] pw = "changeit".toCharArray();
        ks.setKeyEntry("ca", key, pw, new Certificate[] {cert});

        InterceptOptions options = InterceptOptions.builder().ca(ks, pw).build();
        String certOut = field(options, "caCertPem");
        assertTrue(certOut.contains("BEGIN CERTIFICATE"), certOut);
        assertTrue(field(options, "caKeyPem").contains("BEGIN PRIVATE KEY"));
        Certificate roundTrip = CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(certOut.getBytes(StandardCharsets.UTF_8)));
        assertEquals(cert, roundTrip);
    }

    @Test
    void generateCaRequestsReturnCaKey() {
        InterceptOptions options = InterceptOptions.builder().generateCa().build();
        assertEquals(JsonBool.TRUE, ((JsonObject) options.toJson()).get("returnCaKey"));
    }

    @Test
    void generateCaCannotCombineWithASuppliedCa() {
        assertThrows(IllegalArgumentException.class,
                () -> InterceptOptions.builder().generateCa().ca("c", "k").build());
    }

    private static PrivateKey parseKey(String pem) throws Exception {
        String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
