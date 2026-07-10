package io.github.etacassiopeia.rift.examples;

import io.github.etacassiopeia.rift.EmbeddedOptions;
import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.Rift;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import static io.github.etacassiopeia.rift.dsl.RiftDsl.imposter;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.okJson;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.onGet;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.onRequest;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.proxyTo;

/**
 * Parity sample: a {@code proxyOnce} imposter records a real upstream's response the first time a
 * request matches, then replays that recorded response forever after — even once the upstream is
 * gone. Requires JDK 22+ (the embedded engine); to actually run it against a real engine, pass
 * {@code -Drift.ffi.lib=/path/to/librift_ffi.<ext>}.
 */
public final class ProxyRecordReplayExample {

    public static void main(String[] args) throws Exception {
        EmbeddedOptions.Builder options = EmbeddedOptions.builder();
        String lib = System.getProperty("rift.ffi.lib");
        if (lib != null && !lib.isBlank()) {
            options.libraryPath(Path.of(lib));
        }

        try (Rift rift = Rift.embedded(options.build())) {
            Imposter upstream = rift.create(imposter("upstream")
                    .stub(onGet("/quote").willReturn(okJson("{\"quote\":\"carpe diem\"}"))));

            Imposter proxy = rift.create(imposter("proxy-record-replay")
                    .stub(onRequest().willReturn(proxyTo(upstream.uri().toString()).proxyOnce())));

            HttpClient client = HttpClient.newHttpClient();
            String recorded = get(client, proxy.uri() + "/quote");
            System.out.println("Recorded from upstream: " + recorded);

            upstream.delete();

            String replayed = get(client, proxy.uri() + "/quote");
            System.out.println("Replayed without upstream: " + replayed);
        }
    }

    private static String get(HttpClient client, String url) throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
