package io.github.etacassiopeia.rift.natives;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-process stand-in for the rift GitHub release download endpoint: serves {@code ffi-manifest.json}
 * and one {@code librift_ffi-*} file per platform (JDK {@code com.sun.net.httpserver}, zero deps).
 * The manifest carries the true SHA-256 of each served payload; in {@code tamper} mode one file's
 * bytes are mutated after the manifest is built, so its download fails verification.
 */
final class FakeReleaseServer implements AutoCloseable {

    /** platform -> {file name, extension} */
    static final Map<String, String> FILES = new LinkedHashMap<>();

    static {
        FILES.put("darwin-aarch64", "librift_ffi-darwin-aarch64.dylib");
        FILES.put("darwin-x86_64", "librift_ffi-darwin-x86_64.dylib");
        FILES.put("linux-aarch64", "librift_ffi-linux-aarch64.so");
        FILES.put("linux-x86_64", "librift_ffi-linux-x86_64.so");
        FILES.put("linux-x86_64-musl", "librift_ffi-linux-x86_64-musl.so");
        FILES.put("windows-x86_64", "librift_ffi-windows-x86_64.dll");
    }

    static final String TAMPERED_PLATFORM = "darwin-aarch64";

    private final HttpServer server;
    private final Map<String, byte[]> served = new LinkedHashMap<>();
    private final String manifestJson;
    private final AtomicReference<String> lastAuth = new AtomicReference<>();

    FakeReleaseServer(boolean tamper) {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : FILES.entrySet()) {
            byte[] real = ("rift-ffi-" + e.getKey()).getBytes(StandardCharsets.UTF_8);
            hashes.put(e.getKey(), sha256Hex(real));
            byte[] toServe = (tamper && e.getKey().equals(TAMPERED_PLATFORM))
                    ? ("corrupted-" + e.getKey()).getBytes(StandardCharsets.UTF_8)
                    : real;
            served.put(e.getValue(), toServe);
        }
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        this.manifestJson = buildManifest(hashes);
        server.createContext("/", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth != null) {
                lastAuth.set(auth);
            }
            String path = exchange.getRequestURI().getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            // Mirror GitHub release assets: the download URL 302-redirects to a CDN path before the
            // bytes are served. This exercises the client's redirect-following on every fetch.
            if (!path.startsWith("/dl/")) {
                exchange.getResponseHeaders().add("Location", "/dl/" + name);
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
                return;
            }
            byte[] body;
            if (name.equals("ffi-manifest.json")) {
                body = manifestJson.getBytes(StandardCharsets.UTF_8);
            } else {
                body = served.get(name);
            }
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    /** The bytes the manifest hash is computed over (i.e. the correct, untampered payload). */
    static byte[] trueBytes(String platform) {
        return ("rift-ffi-" + platform).getBytes(StandardCharsets.UTF_8);
    }

    /** The bytes the server actually serves for a platform (differs from {@link #trueBytes} when tampered). */
    byte[] servedBytesFor(String platform) {
        return served.get(FILES.get(platform));
    }

    URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    String manifestJson() {
        return manifestJson;
    }

    String lastAuthHeader() {
        return lastAuth.get();
    }

    private String buildManifest(Map<String, String> hashes) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"version\":\"v0.12.0\",\"abi\":\"v2\",\"artifacts\":[");
        boolean first = true;
        for (Map.Entry<String, String> e : FILES.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"platform\":\"").append(e.getKey())
                    .append("\",\"file\":\"").append(e.getValue())
                    .append("\",\"sha256\":\"").append(hashes.get(e.getKey()))
                    .append("\",\"url\":\"http://example.invalid/").append(e.getValue()).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
