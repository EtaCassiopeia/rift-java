package io.github.achirdlabs.rift.conformance;

import io.github.achirdlabs.rift.Imposter;
import io.github.achirdlabs.rift.RecordedPage;
import io.github.achirdlabs.rift.RecordedRequest;
import io.github.achirdlabs.rift.Rift;
import io.github.achirdlabs.rift.Space;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static io.github.achirdlabs.rift.conformance.LiveEngine.engine;
import static io.github.achirdlabs.rift.conformance.LiveEngine.gated;
import static io.github.achirdlabs.rift.dsl.RiftDsl.imposter;
import static io.github.achirdlabs.rift.dsl.RiftDsl.inMemoryFlowState;
import static io.github.achirdlabs.rift.dsl.RiftDsl.okJson;
import static io.github.achirdlabs.rift.dsl.RiftDsl.onGet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Space.recordedPage()}/{@code recordedSince()} against a real engine (#149): the space tail
 * is the imposter journal cut by {@code flow_id}, and the cursor is the <em>imposter's</em> index —
 * it advances past other flows' entries, which only a live engine can prove.
 *
 * <p>Runs on every transport lane. {@code match=} filtering is an admin-API surface, and the
 * embedded transport now reaches one: it delegates {@code recordedSince} to its in-process admin
 * server (#175) rather than refusing the clause. Needs no corpus, just {@code RIFT_IT=1}.
 */
class SpaceCursorIT {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final String FLOW_HEADER = "X-Flow-Id";

    @TestFactory
    Stream<DynamicTest> aSpaceTailFollowsOnlyItsOwnTrafficOnTheImpostersCursor() {
        return gated("space cursor page + tail over a real engine", () -> {
            try (Rift rift = engine()) {
                Imposter imp = rift.create(imposter("space-cursor")
                        .protocol("http")
                        .record()
                        .flowState(inMemoryFlowState().flowIdFromHeader(FLOW_HEADER))
                        .stub(onGet("/a").willReturn(okJson("{\"ok\":true}"))));

                get(imp.uri() + "/alice-1", "alice");
                get(imp.uri() + "/bob-1", "bob");

                Space alice = imp.space("alice");
                RecordedPage baseline = alice.recordedPage();
                assertEquals(List.of("/alice-1"),
                        baseline.requests().stream().map(RecordedRequest::path).toList(),
                        "the baseline sees only alice's traffic");
                assertTrue(baseline.nextIndex().isPresent(), "every lane reports the cursor");
                long cursor = baseline.nextIndex().getAsLong();

                // Only bob records; alice's tail stays empty but her cursor must still advance —
                // it is the imposter's journal index, not a per-space count.
                get(imp.uri() + "/bob-2", "bob");
                RecordedPage idle = alice.recordedSince(cursor);
                assertEquals(List.of(), idle.requests(), "bob's traffic never leaks into alice's tail");
                assertTrue(idle.nextIndex().isPresent(), "the cursor is still reported on an empty page");
                assertTrue(idle.nextIndex().getAsLong() > cursor,
                        "the cursor advances past entries the flow_id clause rejected");

                get(imp.uri() + "/alice-2", "alice");
                RecordedPage tail = alice.recordedSince(idle.nextIndex().getAsLong());
                assertEquals(List.of("/alice-2"),
                        tail.requests().stream().map(RecordedRequest::path).toList(),
                        "the tail resumes exactly where the cursor left off");

                // A space cursor and an imposter cursor are the same number: the imposter's own
                // tail from alice's cursor sees everything recorded after it, any flow.
                assertEquals(List.of("/alice-2"),
                        imp.recordedSince(idle.nextIndex().getAsLong()).requests().stream()
                                .map(RecordedRequest::path).toList(),
                        "space and imposter cursors are interchangeable");
            }
        });
    }

    private static void get(String url, String flowId) throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                        .header(FLOW_HEADER, flowId).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "the request must be served, so it is recorded");
    }
}
