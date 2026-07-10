package io.github.etacassiopeia.rift.examples;

import io.github.etacassiopeia.rift.EmbeddedOptions;
import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.Intercept;
import io.github.etacassiopeia.rift.Rift;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import static io.github.etacassiopeia.rift.dsl.RiftDsl.imposter;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.okJson;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.onGet;

/**
 * Parity sample: an {@link Intercept} {@code redirectTo} rule diverts real HTTPS traffic bound for
 * {@code cdn.optimizely.com} to a local imposter serving a feature-flag datafile — no real network
 * call ever leaves the JVM. Requires JDK 22+ (the embedded engine). This sample points the engine at
 * a cdylib explicitly via {@code -Drift.ffi.lib=/path/to/librift_ffi.<ext>} for a self-contained run;
 * a real consumer instead puts a {@code rift-java-natives} classifier jar on the classpath (see the
 * README) and just calls {@code Rift.embedded()}.
 */
public final class OptimizelyDatafileExample {

    private static final String DATAFILE =
            "{\"revision\":\"42\",\"accountId\":\"acct-1\",\"featureFlags\":[]}";

    public static void main(String[] args) throws Exception {
        EmbeddedOptions.Builder options = EmbeddedOptions.builder();
        String lib = System.getProperty("rift.ffi.lib");
        if (lib != null && !lib.isBlank()) {
            options.libraryPath(Path.of(lib));
        }

        try (Rift rift = Rift.embedded(options.build())) {
            Imposter cdn = rift.create(imposter("optimizely-cdn")
                    .stub(onGet("/datafiles/acct-1.json").willReturn(okJson(DATAFILE))));

            try (Intercept intercept = rift.intercept()) {
                intercept.redirectTo("cdn.optimizely.com", cdn);

                SSLContext trust = intercept.trust().sslContext();
                HttpClient client = HttpClient.newBuilder()
                        .sslContext(trust)
                        .proxy(intercept.proxySelector())
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(URI.create("https://cdn.optimizely.com/datafiles/acct-1.json"))
                                .timeout(Duration.ofSeconds(10))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

                System.out.println("Intercepted datafile: " + response.body());
            }
        }
    }
}
