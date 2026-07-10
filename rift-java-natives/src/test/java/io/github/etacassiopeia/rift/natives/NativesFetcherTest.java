package io.github.etacassiopeia.rift.natives;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the download / SHA-256 / stage pipeline of {@link NativesFetcher} against an in-process
 * release server (no network): the six classifier jars' payloads, the musl classifier remap, hash
 * enforcement, the air-gap ({@code RIFT_NATIVES_DIR}) path, and the mirror/auth overrides.
 */
class NativesFetcherTest {

    /** platform -> expected classifier directory name (musl is the one remap). */
    private static final Map<String, String> EXPECTED_CLASSIFIER = Map.of(
            "darwin-aarch64", "darwin-aarch64",
            "darwin-x86_64", "darwin-x86_64",
            "linux-aarch64", "linux-aarch64",
            "linux-x86_64", "linux-x86_64",
            "linux-x86_64-musl", "linux-musl-x86_64",
            "windows-x86_64", "windows-x86_64");

    private static String ext(String file) {
        return file.substring(file.lastIndexOf('.') + 1);
    }

    @Test
    void stagesAllSixWithBuildInfoAndMuslRemap(@TempDir Path staging) throws Exception {
        try (FakeReleaseServer server = new FakeReleaseServer(false)) {
            Map<String, String> env = new HashMap<>();
            env.put("RIFT_NATIVES_BASE_URL", server.baseUri().toString());

            NativesFetcher.stageAll("0.12.0", staging, env);

            for (Map.Entry<String, String> e : FakeReleaseServer.FILES.entrySet()) {
                String classifier = EXPECTED_CLASSIFIER.get(e.getKey());
                Path dir = staging.resolve("native").resolve(classifier);
                Path lib = dir.resolve("librift_ffi." + ext(e.getValue()));
                assertTrue(Files.exists(lib), "staged " + classifier + " lib");
                assertArrayEquals(FakeReleaseServer.trueBytes(e.getKey()), Files.readAllBytes(lib),
                        "payload for " + classifier);
                Path buildInfo = dir.resolve("BUILD_INFO.json");
                assertTrue(Files.exists(buildInfo), "BUILD_INFO for " + classifier);
                assertTrue(Files.readString(buildInfo).contains(e.getKey()),
                        "BUILD_INFO records the source platform " + e.getKey());
            }
            assertFalse(Files.exists(staging.resolve("native").resolve("linux-x86_64-musl")),
                    "musl platform is remapped to the linux-musl-x86_64 classifier, not kept verbatim");
        }
    }

    @Test
    void hashMismatchFailsNamingExpectedAndActual(@TempDir Path staging) {
        try (FakeReleaseServer server = new FakeReleaseServer(true)) {
            Map<String, String> env = new HashMap<>();
            env.put("RIFT_NATIVES_BASE_URL", server.baseUri().toString());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> NativesFetcher.stageAll("0.12.0", staging, env));
            String msg = ex.getMessage();
            assertTrue(msg.contains(FakeReleaseServer.TAMPERED_PLATFORM)
                            || msg.contains("librift_ffi-" + FakeReleaseServer.TAMPERED_PLATFORM),
                    "names the failing artifact: " + msg);
            String expected = FakeReleaseServer.sha256Hex(FakeReleaseServer.trueBytes(FakeReleaseServer.TAMPERED_PLATFORM));
            assertTrue(msg.contains(expected), "names the expected hash: " + msg);
            String actual = FakeReleaseServer.sha256Hex(server.servedBytesFor(FakeReleaseServer.TAMPERED_PLATFORM));
            assertTrue(msg.contains(actual), "names the actual (downloaded) hash: " + msg);
        }
    }

    /** Writes a valid air-gap bundle (manifest + libs) for the given platforms into {@code dir}. */
    private static void writeAirGapBundle(Path dir, java.util.List<String> platforms) throws Exception {
        StringBuilder arts = new StringBuilder();
        for (String p : platforms) {
            String file = FakeReleaseServer.FILES.get(p);
            byte[] bytes = FakeReleaseServer.trueBytes(p);
            Files.write(dir.resolve(file), bytes);
            if (arts.length() > 0) {
                arts.append(',');
            }
            arts.append("{\"platform\":\"").append(p).append("\",\"file\":\"").append(file)
                    .append("\",\"sha256\":\"").append(FakeReleaseServer.sha256Hex(bytes))
                    .append("\",\"url\":\"http://example.invalid/").append(file).append("\"}");
        }
        Files.writeString(dir.resolve("ffi-manifest.json"),
                "{\"version\":\"v0.12.0\",\"abi\":\"v2\",\"artifacts\":[" + arts + "]}");
    }

    private static RuntimeException stageFromManifest(Path offline, Path staging, String manifest) {
        Map<String, String> env = new HashMap<>();
        env.put("RIFT_NATIVES_DIR", offline.toString());
        return assertThrows(RuntimeException.class, () -> {
            Files.writeString(offline.resolve("ffi-manifest.json"), manifest);
            NativesFetcher.stageAll("0.12.0", staging, env);
        });
    }

    @Test
    void manifestWithoutArtifactsArrayFails(@TempDir Path staging, @TempDir Path offline) {
        RuntimeException ex = stageFromManifest(offline, staging, "{\"version\":\"v0.12.0\"}");
        assertTrue(ex.getMessage().contains("artifacts"), ex.getMessage());
    }

    @Test
    void manifestRootNotAnObjectFails(@TempDir Path staging, @TempDir Path offline) {
        RuntimeException ex = stageFromManifest(offline, staging, "[]");
        assertTrue(ex.getMessage().toLowerCase().contains("object"), ex.getMessage());
    }

    @Test
    void artifactMissingSha256Fails(@TempDir Path staging, @TempDir Path offline) {
        String manifest = "{\"artifacts\":[{\"platform\":\"linux-x86_64\",\"file\":\"librift_ffi-linux-x86_64.so\"}]}";
        RuntimeException ex = stageFromManifest(offline, staging, manifest);
        assertTrue(ex.getMessage().contains("sha256"), ex.getMessage());
    }

    @Test
    void incompleteManifestNamesTheMissingPlatform(@TempDir Path staging, @TempDir Path offline) throws Exception {
        // A valid bundle for every platform EXCEPT windows-x86_64: each present lib verifies, but the
        // post-stage completeness check must fail rather than silently produce an empty windows jar.
        java.util.List<String> all = new java.util.ArrayList<>(FakeReleaseServer.FILES.keySet());
        all.remove("windows-x86_64");
        writeAirGapBundle(offline, all);

        Map<String, String> env = new HashMap<>();
        env.put("RIFT_NATIVES_DIR", offline.toString());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> NativesFetcher.stageAll("0.12.0", staging, env));
        assertTrue(ex.getMessage().contains("missing"), ex.getMessage());
        assertTrue(ex.getMessage().contains("windows-x86_64"), ex.getMessage());
    }

    @Test
    void httpNon200Fails(@TempDir Path staging) throws Exception {
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            Map<String, String> env = new HashMap<>();
            env.put("RIFT_NATIVES_BASE_URL", "http://127.0.0.1:" + server.getAddress().getPort() + "/");
            Exception ex = assertThrows(Exception.class,
                    () -> NativesFetcher.stageAll("0.12.0", staging, env));
            assertTrue(ex.getMessage().contains("500"), ex.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void airGapUsesPreStagedDirWithoutNetwork(@TempDir Path staging, @TempDir Path offline) throws Exception {
        // Pre-stage the manifest + the six lib files the way a mirror/air-gap bundle would.
        try (FakeReleaseServer server = new FakeReleaseServer(false)) {
            Files.writeString(offline.resolve("ffi-manifest.json"), server.manifestJson());
        }
        for (Map.Entry<String, String> e : FakeReleaseServer.FILES.entrySet()) {
            Files.write(offline.resolve(e.getValue()), FakeReleaseServer.trueBytes(e.getKey()));
        }

        Map<String, String> env = new HashMap<>();
        env.put("RIFT_NATIVES_DIR", offline.toString());
        // A dead base URL: if the air-gap path touched the network, this would fail fast.
        env.put("RIFT_NATIVES_BASE_URL", "http://127.0.0.1:1/");

        NativesFetcher.stageAll("0.12.0", staging, env);

        assertArrayEquals(FakeReleaseServer.trueBytes("linux-x86_64"),
                Files.readAllBytes(staging.resolve("native").resolve("linux-x86_64").resolve("librift_ffi.so")));
        assertTrue(Files.exists(staging.resolve("native").resolve("windows-x86_64").resolve("librift_ffi.dll")));
    }

    @Test
    void sendsBearerAuthWhenConfigured(@TempDir Path staging) throws Exception {
        try (FakeReleaseServer server = new FakeReleaseServer(false)) {
            Map<String, String> env = new HashMap<>();
            env.put("RIFT_NATIVES_BASE_URL", server.baseUri().toString());
            env.put("RIFT_NATIVES_AUTH", "secret-token");

            NativesFetcher.stageAll("0.12.0", staging, env);

            assertEquals("Bearer secret-token", server.lastAuthHeader());
        }
    }

    @Test
    void defaultBaseUrlDerivesFromVersionTag() {
        // Guards the release-tag convention (v-prefixed) that the default URL is built from.
        assertTrue(NativesFetcher.defaultBaseUrl("0.12.0").endsWith("/v0.12.0/"),
                NativesFetcher.defaultBaseUrl("0.12.0"));
        assertTrue(NativesFetcher.defaultBaseUrl("0.12.0").contains("releases/download"));
    }
}
