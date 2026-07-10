package io.github.etacassiopeia.rift.spawn;

import io.github.etacassiopeia.rift.SpawnOptions;
import io.github.etacassiopeia.rift.error.EngineUnavailable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Resolves the {@code rift} engine binary to run, in the same order rift-node uses: an explicit
 * path, the {@code RIFT_BINARY_PATH} environment variable, a {@code PATH} lookup, a version-pinned
 * local cache, and finally a download-and-verify from a release mirror.
 */
public final class BinaryResolver {

    private static final String DEFAULT_MIRROR = "https://github.com/EtaCassiopeia/rift/releases/download";

    private BinaryResolver() {
    }

    public static Path resolve(SpawnOptions opts) {
        return resolve(opts, System.getenv());
    }

    static Path resolve(SpawnOptions opts, Map<String, String> env) {
        if (opts.binaryPath().isPresent()) {
            return validateBinary(opts.binaryPath().get(), "binaryPath option");
        }

        List<String> tried = new ArrayList<>();

        String envBinaryPath = env.get("RIFT_BINARY_PATH");
        if (envBinaryPath != null && !envBinaryPath.isBlank()) {
            return validateBinary(Path.of(envBinaryPath), "RIFT_BINARY_PATH");
        }
        tried.add("RIFT_BINARY_PATH environment variable: not set");

        TargetPlatform platform = TargetPlatform.current();

        Path onPath = findOnPath(env.get("PATH"), platform);
        if (onPath != null) {
            return onPath;
        }
        tried.add("PATH lookup: no rift/rift-http-proxy executable found on PATH");

        Path home = homeDir(env, platform);
        Path cacheBinary = versionCacheDir(home, opts.version()).resolve(platform.binaryName());
        // require executable, not merely present: a half-downloaded / non-executable cache entry must be
        // rejected and re-downloaded rather than returned (and then reused) forever.
        if (Files.isRegularFile(cacheBinary) && Files.isExecutable(cacheBinary)) {
            return cacheBinary;
        }
        tried.add("version cache (" + cacheBinary + "): no executable rift binary");

        if (env.get("RIFT_OFFLINE") != null || env.get("RIFT_SKIP_BINARY_DOWNLOAD") != null) {
            throw new EngineUnavailable(
                    "no rift binary found and binary downloads are disabled; steps tried:\n  - "
                            + String.join("\n  - ", tried));
        }

        return download(opts, env, platform, home);
    }

    /** Validates a caller/env-supplied binary path so a stale pointer fails at resolution with a clear message. */
    private static Path validateBinary(Path path, String source) {
        if (!Files.isRegularFile(path) || !Files.isExecutable(path)) {
            throw new EngineUnavailable(source + " points at a missing or non-executable file: " + path);
        }
        return path;
    }

    private static Path findOnPath(String pathEnv, TargetPlatform platform) {
        if (pathEnv == null || pathEnv.isBlank()) {
            return null;
        }
        List<String> candidates = platform.isWindows()
                ? List.of(platform.binaryName(), "rift-http-proxy.exe")
                : List.of(platform.binaryName(), "rift-http-proxy");
        for (String dir : pathEnv.split(Pattern.quote(File.pathSeparator))) {
            if (dir.isBlank()) {
                continue;
            }
            for (String candidate : candidates) {
                Path p = Path.of(dir, candidate);
                if (Files.isRegularFile(p) && Files.isExecutable(p)) {
                    return p;
                }
            }
        }
        return null;
    }

    private static Path homeDir(Map<String, String> env, TargetPlatform platform) {
        String home = env.get(platform.isWindows() ? "USERPROFILE" : "HOME");
        if (home == null || home.isBlank()) {
            home = System.getProperty("user.home");
        }
        return Path.of(home);
    }

    private static Path versionCacheDir(Path home, String version) {
        return home.resolve(".cache").resolve("rift-java").resolve("binaries").resolve("rift-" + version);
    }

    private static Path download(SpawnOptions opts, Map<String, String> env, TargetPlatform platform, Path home) {
        String base = opts.mirrorUrl().map(URI::toString).orElse(DEFAULT_MIRROR);
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String archiveName = platform.archiveName(opts.version());
        String archiveUrlStr = base + "/v" + opts.version() + "/" + archiveName;
        URI archiveUri = URI.create(archiveUrlStr);
        URI shaUri = URI.create(archiveUrlStr + ".sha256");

        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        byte[] archiveBytes = fetch(client, archiveUri);

        // Only fetch the .sha256 sidecar when we'll actually check it — a private mirror without a
        // sidecar is exactly why RIFT_SKIP_CHECKSUM exists, so skipping must not still require it.
        if (env.get("RIFT_SKIP_CHECKSUM") == null) {
            String expected = firstToken(new String(fetch(client, shaUri), StandardCharsets.UTF_8));
            String actual = sha256Hex(archiveBytes);
            if (!expected.equalsIgnoreCase(actual)) {
                throw new EngineUnavailable("SHA-256 mismatch for " + archiveUri
                        + ": expected " + expected + " but downloaded archive hashed to " + actual);
            }
        }

        Path destDir = versionCacheDir(home, opts.version());
        Path archiveFile = destDir.resolve(archiveName);
        try {
            Files.createDirectories(destDir);
            Files.write(archiveFile, archiveBytes);
        } catch (IOException e) {
            throw new EngineUnavailable("cannot stage downloaded archive at " + archiveFile + ": " + e.getMessage(), e);
        }

        unpack(archiveFile, destDir, platform);

        Path binary = locateBinary(destDir, platform)
                .orElseThrow(() -> new EngineUnavailable(
                        "downloaded archive " + archiveName + " did not contain a rift binary"));
        if (!binary.toFile().setExecutable(true) && !Files.isExecutable(binary)) {
            throw new EngineUnavailable("cannot make the downloaded rift binary executable: " + binary);
        }
        return binary;
    }

    private static byte[] fetch(HttpClient client, URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new EngineUnavailable("failed to download " + uri + ": HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new EngineUnavailable("failed to download " + uri + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineUnavailable("interrupted while downloading " + uri, e);
        }
    }

    private static String firstToken(String s) {
        String trimmed = s.strip();
        int end = 0;
        while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) {
            end++;
        }
        return trimmed.substring(0, end);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is a mandatory JDK algorithm", e);
        }
    }

    private static void unpack(Path archiveFile, Path destDir, TargetPlatform platform) {
        if (platform.isWindows()) {
            unzip(archiveFile, destDir);
        } else {
            untarGz(archiveFile, destDir);
        }
    }

    private static void untarGz(Path archiveFile, Path destDir) {
        try {
            Process p = new ProcessBuilder("tar", "-xzf", archiveFile.toString(), "-C", destDir.toString())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = p.waitFor();
            if (exit != 0) {
                throw new EngineUnavailable("tar extraction of " + archiveFile + " failed (exit " + exit + "): " + output);
            }
        } catch (IOException e) {
            throw new EngineUnavailable("cannot unpack " + archiveFile + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineUnavailable("interrupted while unpacking " + archiveFile, e);
        }
    }

    private static void unzip(Path archiveFile, Path destDir) {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(archiveFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = destDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(destDir)) {
                    throw new EngineUnavailable("zip entry escapes destination directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new EngineUnavailable("cannot unpack " + archiveFile + ": " + e.getMessage(), e);
        }
    }

    private static Optional<Path> locateBinary(Path destDir, TargetPlatform platform) {
        List<String> names = platform.isWindows()
                ? List.of("rift.exe", "rift-http-proxy.exe")
                : List.of("rift", "rift-http-proxy");
        try (Stream<Path> walk = Files.walk(destDir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> names.contains(p.getFileName().toString()))
                    .findFirst();
        } catch (IOException e) {
            throw new EngineUnavailable("cannot search " + destDir + " for the rift binary: " + e.getMessage(), e);
        }
    }
}
