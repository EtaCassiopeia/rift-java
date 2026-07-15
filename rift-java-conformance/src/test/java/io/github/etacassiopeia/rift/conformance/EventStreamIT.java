package io.github.etacassiopeia.rift.conformance;

import io.github.etacassiopeia.rift.EventStream;
import io.github.etacassiopeia.rift.EventStreamOptions;
import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.RecordedPage;
import io.github.etacassiopeia.rift.RecordedRequest;
import io.github.etacassiopeia.rift.Rift;
import io.github.etacassiopeia.rift.RiftEvent;
import io.github.etacassiopeia.rift.SpawnOptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static io.github.etacassiopeia.rift.dsl.RiftDsl.imposter;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.okJson;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.onGet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The admin event stream against a real engine. The unit tests pin framing and lifecycle against a
 * fake; these pin the two claims a fake would simply agree with — that the frames the engine really
 * emits parse into these events, and that the stream's {@code index} is the same journal index the
 * polling side reports, which is the entire basis of the reconcile story.
 *
 * <p>Gated to {@link ConformanceTransport#SPAWN}: {@code /events} is an admin-HTTP endpoint the
 * in-process FFI transport does not serve. Needs no corpus, just {@code RIFT_IT=1}.
 */
class EventStreamIT {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final int PORT = 4597;

    @TestFactory
    Stream<DynamicTest> theStreamPushesWhatTheJournalRecords() {
        return gated("events arrive, and carry the journal's own index", () -> {
            try (Rift rift = Rift.spawn(SpawnOptions.builder().build())) {
                Imposter imp = recordingImposter(rift);

                try (EventStream events = rift.events(EventStreamOptions.builder().port(PORT).build())) {
                    Iterator<RiftEvent> it = events.iterator();

                    RiftEvent.Hello hello = assertInstanceOf(RiftEvent.Hello.class, it.next(),
                            "the engine opens with hello");
                    assertTrue(hello.engineVersion().startsWith("0.13."), hello.engineVersion());

                    get(imp.uri() + "/pushed");

                    RiftEvent.RequestRecorded pushed = assertInstanceOf(RiftEvent.RequestRecorded.class, it.next());
                    assertEquals(PORT, pushed.port());
                    assertEquals("/pushed", pushed.request().path());
                    assertTrue(pushed.seq().isPresent(), "a request event carries its sequence id");

                    // The load-bearing claim: the pushed index and the polled cursor are the same
                    // number. If they were not, reconciling from a stream index would silently skip
                    // or replay, which is the bug the cursor exists to remove.
                    RecordedPage page = imp.recordedPage();
                    assertEquals(page.nextIndex(), pushed.index(),
                            "the stream's index IS the journal cursor");
                }
            }
        });
    }

    @TestFactory
    Stream<DynamicTest> aBrokenStreamIsRecoveredByPollingFromTheLastIndexSeen() {
        return gated("the reconcile loop, end to end", () -> {
            try (Rift rift = Rift.spawn(SpawnOptions.builder().build())) {
                Imposter imp = recordingImposter(rift);
                long cursor;

                try (EventStream events = rift.events(EventStreamOptions.builder().port(PORT).build())) {
                    Iterator<RiftEvent> it = events.iterator();
                    assertInstanceOf(RiftEvent.Hello.class, it.next());

                    get(imp.uri() + "/seen");
                    RiftEvent.RequestRecorded seen = assertInstanceOf(RiftEvent.RequestRecorded.class, it.next());
                    cursor = seen.index().orElseThrow();
                }
                // The stream is gone. Anything recorded now is invisible to it — this is the hole a
                // lagged event or a dropped connection leaves, reproduced deterministically.
                get(imp.uri() + "/missed-1");
                get(imp.uri() + "/missed-2");

                RecordedPage missed = imp.recordedSince(cursor);

                assertEquals(List.of("/missed-1", "/missed-2"),
                        missed.requests().stream().map(RecordedRequest::path).toList(),
                        "polling from the last index seen yields exactly what the stream missed");
                assertTrue(missed.nextIndex().getAsLong() > cursor, "and hands back a cursor to resume from");
            }
        });
    }

    @TestFactory
    Stream<DynamicTest> lifecycleEventsArriveOnTheSameStream() {
        return gated("an imposter change is pushed too", () -> {
            try (Rift rift = Rift.spawn(SpawnOptions.builder().build())) {
                Imposter imp = recordingImposter(rift);

                try (EventStream events = rift.events(EventStreamOptions.builder()
                        .types(EventStreamOptions.EventType.LIFECYCLE)
                        .build())) {
                    Iterator<RiftEvent> it = events.iterator();
                    assertInstanceOf(RiftEvent.Hello.class, it.next());

                    imp.addStub(onGet("/added").willReturn(okJson("{}")));

                    RiftEvent.ImposterChanged changed =
                            assertInstanceOf(RiftEvent.ImposterChanged.class, it.next());
                    assertEquals(RiftEvent.ImposterChanged.Action.STUBS_CHANGED, changed.action());
                    assertEquals(PORT, changed.port().orElseThrow());
                }
            }
        });
    }

    private static Imposter recordingImposter(Rift rift) {
        return rift.create(imposter("events")
                .port(PORT)
                .protocol("http")
                .record()
                .stub(onGet("/pushed").willReturn(okJson("{\"ok\":true}"))));
    }

    private static void get(String url) throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "the request must be served, so it is recorded");
    }

    /** Reports the two skip conditions separately so a lane that silently lost RIFT_IT is diagnosable. */
    private static Stream<DynamicTest> gated(String name, Executable body) {
        return Stream.of(DynamicTest.dynamicTest(name, () -> {
            assumeTrue(integrationEnabled(), "set RIFT_IT=1 to run the live-engine event-stream lane");
            assumeTrue(ConformanceTransport.selected() == ConformanceTransport.SPAWN,
                    "/events is an admin-HTTP endpoint; the FFI transport serves none — SPAWN lane only");
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
