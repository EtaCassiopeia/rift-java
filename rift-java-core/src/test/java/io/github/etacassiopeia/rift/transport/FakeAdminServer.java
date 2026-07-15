package io.github.etacassiopeia.rift.transport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * A minimal in-process stand-in for the Rift admin API, built on the JDK's {@code
 * com.sun.net.httpserver} (zero external deps). Tests register per-route handlers returning a
 * {@link Response}; every received request is recorded for assertions (path, method, body, headers).
 */
final class FakeAdminServer implements AutoCloseable {

    record Received(String method, String path, String body, Map<String, String> headers) {}

    record Response(int status, String body, Map<String, String> headers) {

        Response(int status, String body) {
            this(status, body, Map.of());
        }

        static Response ok(String body) {
            return new Response(200, body);
        }

        Response withHeader(String name, String value) {
            Map<String, String> merged = new LinkedHashMap<>(headers);
            merged.put(name, value);
            return new Response(status, body, merged);
        }
    }

    private final HttpServer server;
    private final List<Received> received = new ArrayList<>();
    // key: "METHOD /path"  (exact) or "METHOD /prefix*" (prefix) → handler
    private final Map<String, BiFunction<Received, Void, Response>> routes = new ConcurrentHashMap<>();

    FakeAdminServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        server.createContext("/", this::dispatch);
        server.start();
    }

    URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    List<Received> received() {
        return List.copyOf(received);
    }

    /** Registers a handler; {@code key} is "METHOD /exact/path" or "METHOD /prefix" (prefix match). */
    FakeAdminServer on(String key, BiFunction<Received, Void, Response> handler) {
        routes.put(key, handler);
        return this;
    }

    FakeAdminServer respond(String key, int status, String body) {
        return on(key, (r, v) -> new Response(status, body));
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        // raw (undecoded) path/query so percent-encoding of caller-supplied segments is observable
        String path = exchange.getRequestURI().getRawPath();
        String query = exchange.getRequestURI().getRawQuery();
        String fullPath = query == null ? path : path + "?" + query;
        String body = readBody(exchange.getRequestBody());
        Map<String, String> headers = new ConcurrentHashMap<>();
        exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), String.join(",", v)));
        Received r = new Received(method, fullPath, body, headers);
        received.add(r);

        BiFunction<Received, Void, Response> handler = resolve(method, path);
        Response response = handler != null ? handler.apply(r, null) : new Response(404, "{\"errors\":[{\"code\":\"no such resource\",\"message\":\"not found\"}]}");
        byte[] out = response.body() == null ? new byte[0] : response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        response.headers().forEach(exchange.getResponseHeaders()::add);
        exchange.sendResponseHeaders(response.status(), out.length == 0 ? -1 : out.length);
        try (var os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    private BiFunction<Received, Void, Response> resolve(String method, String path) {
        BiFunction<Received, Void, Response> exact = routes.get(method + " " + path);
        if (exact != null) {
            return exact;
        }
        for (var e : routes.entrySet()) {
            String[] parts = e.getKey().split(" ", 2);
            if (parts[0].equals(method) && parts[1].endsWith("*") && path.startsWith(parts[1].substring(0, parts[1].length() - 1))) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String readBody(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
