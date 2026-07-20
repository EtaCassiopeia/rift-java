package io.github.achirdlabs.rift.testcontainers;

import io.github.achirdlabs.rift.Imposter;
import io.github.achirdlabs.rift.RecordMode;
import io.github.achirdlabs.rift.RecordSpec;
import io.github.achirdlabs.rift.Recording;
import io.github.achirdlabs.rift.Rift;
import io.github.achirdlabs.rift.dsl.RequestField;
import io.github.achirdlabs.rift.model.Stub;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static io.github.achirdlabs.rift.dsl.RiftDsl.imposter;
import static io.github.achirdlabs.rift.dsl.RiftDsl.okJson;
import static io.github.achirdlabs.rift.dsl.RiftDsl.onGet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The proxy record/replay round-trip (#25 Part 1, AC1 + AC3) against a REAL rift engine in Docker.
 * Gated on {@code RIFT_IT} like the other RiftContainer ITs. Both imposters live in the one engine,
 * so the proxy targets the upstream via the container-internal {@code http://localhost:<port>}.
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RIFT_IT", matches = "1|true")
class RecordReplayIT {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Container
    static final RiftContainer RIFT = new RiftContainer().withImposterPorts(4600, 4545, 4546, 4700);

    // AC1: record → stop → replay. The second request serves the recorded response with the
    // upstream deleted — proving the proxy stub was swapped for the recorded stub.
    @Test
    void recordStopReplayRoundTrip() throws Exception {
        try (Rift client = RIFT.client()) {
            Imposter upstream = client.create(imposter("upstream").port(4600)
                    .stub(onGet("/u/1").willReturn(okJson("{\"id\":1,\"src\":\"upstream\"}"))));
            Imposter proxy = client.create(imposter("proxy").port(4545).record());

            Recording recording = proxy.startRecording("http://localhost:4600");
            HttpResponse<String> recorded = get(proxy.uri() + "/u/1");
            assertEquals(200, recorded.statusCode());
            assertTrue(recorded.body().contains("\"src\":\"upstream\""), recorded.body());

            List<Stub> stubs = recording.stop();
            assertFalse(stubs.isEmpty(), "stop() returns the recorded stubs");

            upstream.delete();

            HttpResponse<String> replayed = get(proxy.uri() + "/u/1");
            assertEquals(200, replayed.statusCode(), "replay serves with upstream down");
            assertTrue(replayed.body().contains("\"src\":\"upstream\""), "replay body identical: " + replayed.body());
        }
    }

    // AC3: ignoreHeaders strips volatile headers from generated predicates, so a recorded stub
    // matches regardless of that header's value — verified purely by HTTP behavior: capture with a
    // header generator + ignoreHeaders, then replay with a DIFFERENT header value still matches.
    @Test
    void ignoreHeadersMakesRecordedStubMatchAnyValue() throws Exception {
        try (Rift client = RIFT.client()) {
            client.create(imposter("origin2").port(4700)
                    .stub(onGet("/p").willReturn(okJson("{\"ok\":true}"))));
            Imposter proxy = client.create(imposter("proxy2").port(4546).record());

            RecordSpec spec = RecordSpec.builder()
                    .mode(RecordMode.ONCE)
                    .generateBy(RequestField.METHOD, RequestField.PATH, RequestField.HEADERS)
                    .ignoreHeaders("X-Request-Id")
                    .build();
            Recording recording = proxy.startRecording("http://localhost:4700", spec);

            // capture with one X-Request-Id
            assertEquals(200, send(proxy.uri() + "/p", "X-Request-Id", "aaa").statusCode());
            recording.stop();

            // replay with a DIFFERENT X-Request-Id — matches only if that header was ignored
            HttpResponse<String> replayed = send(proxy.uri() + "/p", "X-Request-Id", "zzz");
            assertEquals(200, replayed.statusCode(), "recorded stub matches despite a different X-Request-Id");
            assertTrue(replayed.body().contains("\"ok\":true"), replayed.body());
        }
    }

    private static HttpResponse<String> get(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> send(String url, String header, String value) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                        .header(header, value).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
