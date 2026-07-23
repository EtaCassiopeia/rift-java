package io.github.achirdlabs.rift.conformance;

import io.github.achirdlabs.rift.Imposter;
import io.github.achirdlabs.rift.RecordedPage;
import io.github.achirdlabs.rift.Rift;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.Stream;

import static io.github.achirdlabs.rift.conformance.LiveEngine.engine;
import static io.github.achirdlabs.rift.conformance.LiveEngine.gated;
import static io.github.achirdlabs.rift.dsl.RiftDsl.imposter;
import static io.github.achirdlabs.rift.dsl.RiftDsl.okJson;
import static io.github.achirdlabs.rift.dsl.RiftDsl.onGet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The savedRequests journal cursor (rift#603) against a real engine — the contract a request tail
 * depends on, exercised end-to-end rather than against a fake that could agree with a wrong reading
 * of it.
 *
 * <p>Runs on every transport lane. The cursor rides in HTTP response headers, but the embedded
 * transport reaches an admin API too — it delegates {@code recordedSince} to its in-process admin
 * server (#175), exactly as it does {@code events} — so these are the same semantics there, not a
 * fallback. Needs no corpus, just {@code RIFT_IT=1}.
 */
class RecordedCursorIT {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @TestFactory
    Stream<DynamicTest> cursorContract() {
        return gated("the savedRequests cursor over a real engine", () -> {
            try (Rift rift = engine()) {
                Imposter imp = rift.create(imposter("cursor")
                        .protocol("http")
                        .record()
                        .stub(onGet("/hit").willReturn(okJson("{\"ok\":true}"))));

                // Baseline on an empty journal: a real cursor of 0 ("nothing recorded yet"), which
                // is emphatically not the absent cursor that means "this engine has none".
                RecordedPage empty = imp.recordedPage();
                assertTrue(empty.requests().isEmpty());
                assertEquals(OptionalLong.of(0), empty.nextIndex(), "0 is a cursor, not an absence");
                assertFalse(empty.truncated());

                get(imp.uri() + "/hit");
                get(imp.uri() + "/hit");

                RecordedPage baseline = imp.recordedPage();
                assertEquals(2, baseline.requests().size());
                assertTrue(baseline.nextIndex().isPresent(), "every lane must report a cursor");
                long cursor = baseline.nextIndex().getAsLong();
                assertFalse(baseline.truncated(), "a baseline read never reports truncation");

                // At the tip: nothing new, but the cursor is still reported. A tail that read this
                // as "unsupported" would stop advancing and re-scan the journal forever.
                RecordedPage atTip = imp.recordedSince(cursor);
                assertTrue(atTip.requests().isEmpty(), "no entries are newer than the tip");
                assertEquals(OptionalLong.of(cursor), atTip.nextIndex(), "the tip still carries its cursor");
                assertFalse(atTip.truncated());

                // since is exclusive: only what arrived after the cursor, not the whole journal.
                get(imp.uri() + "/hit/3");
                RecordedPage tail = imp.recordedSince(cursor);
                assertEquals(1, tail.requests().size(), "only the entry recorded after the cursor");
                assertEquals("/hit/3", tail.requests().get(0).path());
                assertTrue(tail.nextIndex().getAsLong() > cursor, "the cursor advanced past what it served");
                long afterTail = tail.nextIndex().getAsLong();

                // Indices survive a clear: deleting data you asked to delete is not a hole, so a
                // cursor held across it stays valid and is not flagged truncated.
                imp.clearRecorded();
                assertTrue(imp.recorded().isEmpty(), "the clear really emptied the journal");
                get(imp.uri() + "/hit/4");

                RecordedPage afterClear = imp.recordedSince(afterTail);
                assertEquals(1, afterClear.requests().size(), "a cursor held across a clear still tails");
                assertEquals("/hit/4", afterClear.requests().get(0).path());
                assertFalse(afterClear.truncated(), "a clear is not a retention hole");
                assertTrue(afterClear.nextIndex().getAsLong() > afterTail, "indices are not reset by a clear");
            }
        });
    }

    private static void get(String url) throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "the imposter must serve, so the request is recorded");
    }
}
