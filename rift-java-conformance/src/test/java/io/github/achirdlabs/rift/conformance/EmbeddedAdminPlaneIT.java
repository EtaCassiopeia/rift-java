package io.github.achirdlabs.rift.conformance;

import io.github.achirdlabs.rift.EmbeddedOptions;
import io.github.achirdlabs.rift.Imposter;
import io.github.achirdlabs.rift.Rift;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.Stream;

import static io.github.achirdlabs.rift.dsl.RiftDsl.imposter;
import static io.github.achirdlabs.rift.dsl.RiftDsl.okJson;
import static io.github.achirdlabs.rift.dsl.RiftDsl.onGet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link EmbeddedOptions}' admin-plane settings against the real in-process server (#176): the
 * engine is handed {@code host}/{@code port}/{@code apiKey}, so these prove the settings actually
 * reach {@code rift_serve_admin} rather than being accepted and ignored.
 *
 * <p>EMBEDDED lane only, and not by convenience: there is no in-process admin plane to configure on
 * a spawned engine, so this is not a claim that generalises across transports. Needs no corpus,
 * just {@code RIFT_IT=1}.
 */
class EmbeddedAdminPlaneIT {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @TestFactory
    Stream<DynamicTest> anApiKeyGuardsTheInProcessAdminPlane() {
        return gated("the apiKey reaches rift_serve_admin and is enforced", () -> {
            try (Rift rift = Rift.embedded(EmbeddedOptions.builder().apiKey("s3cret-token").build())) {
                Imposter imp = recordingImposter(rift);
                URI admin = rift.adminUri();

                // The load-bearing assertion. An apiKey that never reached the engine leaves the
                // loopback admin plane wide open to anything on this host — accepting the setting
                // and ignoring it is worse than rejecting it, because the caller believes it took.
                assertEquals(401, get(admin.resolve("/imposters"), null).statusCode(),
                        "an unauthenticated call must be refused");
                assertEquals(200, get(admin.resolve("/imposters"), "s3cret-token").statusCode(),
                        "the same call with the key is served");

                // ...and the SDK's own delegated ops must still work, i.e. the transport sends the
                // key it configured. Locking the door without keeping a key would be the other bug.
                assertTrue(imp.recordedPage().nextIndex().isPresent(),
                        "the delegating client authenticates itself");
            }
        });
    }

    @TestFactory
    Stream<DynamicTest> theAdminPortIsPinnedWhenAsked() {
        return gated("the adminPort reaches rift_serve_admin", () -> {
            int wanted = freePort();
            try (Rift rift = Rift.embedded(EmbeddedOptions.builder().adminPort(wanted).build())) {
                assertEquals(wanted, rift.adminUri().getPort(),
                        "the admin server binds the port that was asked for");
            }
        });
    }

    @TestFactory
    Stream<DynamicTest> theAdminHostIsHonouredWhenAsked() {
        return gated("the adminHost reaches rift_serve_admin", () -> {
            // Binding the wildcard needs no inbound reachability, so this is safe on a sandboxed
            // runner — and it is decisive: adminUri() is built from the URL the engine reports back,
            // not echoed from these options, so a host that never reached the engine would come
            // back as the default 127.0.0.1 rather than what was asked for.
            try (Rift rift = Rift.embedded(EmbeddedOptions.builder().adminHost("0.0.0.0").build())) {
                assertEquals("0.0.0.0", rift.adminUri().getHost(),
                        "the admin server binds the host that was asked for");
            }
        });
    }

    @TestFactory
    Stream<DynamicTest> theDefaultsAreUnchanged() {
        return gated("no options still means loopback, ephemeral, unauthenticated", () -> {
            try (Rift rift = Rift.embedded()) {
                recordingImposter(rift);
                URI admin = rift.adminUri();

                assertEquals("127.0.0.1", admin.getHost(), "the default admin host is loopback");
                assertNotEquals(0, admin.getPort(), "port 0 means OS-assigned, and is reported back resolved");
                assertEquals(200, get(admin.resolve("/imposters"), null).statusCode(),
                        "no apiKey configured means no apiKey required");
            }
        });
    }

    /** An OS-assigned free port, released before use — never a literal (#162). */
    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private static Imposter recordingImposter(Rift rift) {
        return rift.create(imposter("admin-plane")
                .protocol("http")
                .record()
                .stub(onGet("/ping").willReturn(okJson("{\"ok\":true}"))));
    }

    private static HttpResponse<String> get(URI uri, String apiKey) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET();
        if (apiKey != null) {
            builder.header("Authorization", apiKey);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Reports the skip conditions separately so a lane that silently lost RIFT_IT is diagnosable. */
    private static Stream<DynamicTest> gated(String name, Executable body) {
        return Stream.of(DynamicTest.dynamicTest(name, () -> {
            assumeTrue(integrationEnabled(), "set RIFT_IT=1 to run the live-engine admin-plane lane");
            assumeTrue(ConformanceTransport.selected() == ConformanceTransport.EMBEDDED,
                    "only the embedded transport has an in-process admin plane to configure");
            assumeTrue(ConformanceTransport.EMBEDDED.isAvailable(),
                    "the embedded lane needs a librift_ffi");
            body.run();
        }));
    }

    @FunctionalInterface
    private interface Executable {
        void run() throws Exception;
    }

    private static boolean integrationEnabled() {
        String it = System.getenv("RIFT_IT");
        return it != null && !it.isBlank() && !it.equals("0");
    }
}
