package io.github.achirdlabs.rift.spawn;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * A stand-in for the {@code rift} binary, used by {@link SpawnLifecycleTest} via a generated launcher
 * script. It scans the forwarded {@code rift start --port N ...} arguments for {@code --port} and serves
 * {@code GET /imposters} (and everything else) with {@code 200 []} on that port, then blocks until killed.
 *
 * <p>Two test modes select degenerate behaviors: {@code --crash} exits non-zero immediately (crash
 * detection); {@code --hang} blocks forever without ever binding the port (startup-timeout).
 */
public final class FakeRift {

    private FakeRift() {}

    public static void main(String[] args) throws Exception {
        int port = -1;
        boolean crash = false;
        boolean hang = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> {
                    if (i + 1 < args.length) {
                        port = Integer.parseInt(args[i + 1]);
                    }
                }
                case "--crash" -> crash = true;
                case "--hang" -> hang = true;
                default -> { }
            }
        }
        if (crash) {
            System.err.println("fake rift: simulated crash");
            System.exit(1);
        }
        if (hang) {
            System.out.println("fake rift hanging without binding a port");
            System.out.flush();
            Thread.sleep(Long.MAX_VALUE);
            return;
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", exchange -> {
            // POST /imposters returns a created imposter (so a real admin round-trip can be exercised);
            // everything else (incl. the GET /imposters health check) answers 200 [].
            String responseBody = exchange.getRequestMethod().equals("POST")
                    && exchange.getRequestURI().getPath().equals("/imposters")
                    ? "{\"port\":4545,\"protocol\":\"http\",\"stubs\":[]}" : "[]";
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        Thread.sleep(Long.MAX_VALUE);
    }
}
