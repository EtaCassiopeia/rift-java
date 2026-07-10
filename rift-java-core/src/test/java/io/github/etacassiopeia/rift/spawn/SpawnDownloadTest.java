package io.github.etacassiopeia.rift.spawn;

import com.sun.net.httpserver.HttpServer;
import io.github.etacassiopeia.rift.SpawnOptions;
import io.github.etacassiopeia.rift.error.EngineUnavailable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The download step (resolution step 6) against a fake release server. This validates the
 * download → SHA-256-verify → unpack logic that the real {@code rift} release would exercise; the
 * external release artifact itself is not needed. Unix-only (the fixture builds a {@code sh}-script
 * fake binary and a {@code tar.gz}).
 */
@DisabledOnOs(OS.WINDOWS)
class SpawnDownloadTest {

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** Builds a tiny tar.gz containing an executable {@code rift} script, returns its bytes. */
    private static byte[] buildTarball(Path dir) throws Exception {
        Path stage = Files.createDirectories(dir.resolve("stage"));
        Path bin = stage.resolve("rift");
        Files.writeString(bin, "#!/bin/sh\necho fake rift\n");
        bin.toFile().setExecutable(true);
        Path tarball = dir.resolve("rift.tar.gz");
        Process p = new ProcessBuilder("tar", "-czf", tarball.toString(), "-C", stage.toString(), "rift")
                .redirectErrorStream(true).start();
        if (p.waitFor() != 0) {
            throw new AssertionError("tar failed building the fixture");
        }
        return Files.readAllBytes(tarball);
    }

    private static HttpServer serve(byte[] tarball, String shaLine) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestURI().getPath().endsWith(".sha256")
                    ? shaLine.getBytes(StandardCharsets.UTF_8) : tarball;
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return server;
    }

    @Test
    void downloadsVerifiesAndUnpacksIntoCache(@TempDir Path dir) throws Exception {
        byte[] tarball = buildTarball(dir);
        HttpServer server = serve(tarball, sha256(tarball) + "  rift.tar.gz");
        try {
            Path home = Files.createDirectories(dir.resolve("home"));
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            Path resolved = BinaryResolver.resolve(
                    SpawnOptions.builder().version("0.0.0").mirrorUrl(base).build(),
                    Map.of("HOME", home.toString(), "PATH", ""));
            assertTrue(Files.isExecutable(resolved), "downloaded rift binary must be executable: " + resolved);
            assertTrue(resolved.getFileName().toString().startsWith("rift"), "resolved to a rift binary: " + resolved);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void skipChecksumBypassesVerificationAndDoesNotRequireTheSidecar(@TempDir Path dir) throws Exception {
        byte[] tarball = buildTarball(dir);
        // the .sha256 sidecar 404s; with RIFT_SKIP_CHECKSUM the resolver must not fetch or require it
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith(".sha256")) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, tarball.length);
            try (var os = exchange.getResponseBody()) {
                os.write(tarball);
            }
        });
        server.start();
        try {
            Path home = Files.createDirectories(dir.resolve("home"));
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            Path resolved = BinaryResolver.resolve(
                    SpawnOptions.builder().version("0.0.0").mirrorUrl(base).build(),
                    Map.of("HOME", home.toString(), "PATH", "", "RIFT_SKIP_CHECKSUM", "1"));
            assertTrue(Files.isExecutable(resolved), "resolved binary must be executable: " + resolved);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sha256MismatchThrowsEngineUnavailable(@TempDir Path dir) throws Exception {
        byte[] tarball = buildTarball(dir);
        HttpServer server = serve(tarball, "0000000000000000000000000000000000000000000000000000000000000000  rift.tar.gz");
        try {
            Path home = Files.createDirectories(dir.resolve("home"));
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            assertThrows(EngineUnavailable.class, () -> BinaryResolver.resolve(
                    SpawnOptions.builder().version("0.0.0").mirrorUrl(base).build(),
                    Map.of("HOME", home.toString(), "PATH", "")));
        } finally {
            server.stop(0);
        }
    }
}
