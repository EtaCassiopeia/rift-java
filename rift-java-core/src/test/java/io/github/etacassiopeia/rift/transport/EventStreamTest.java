package io.github.etacassiopeia.rift.transport;

import io.github.etacassiopeia.rift.ConnectOptions;
import io.github.etacassiopeia.rift.EventStream;
import io.github.etacassiopeia.rift.EventStreamOptions;
import io.github.etacassiopeia.rift.MatchClause;
import io.github.etacassiopeia.rift.Rift;
import io.github.etacassiopeia.rift.RiftEvent;
import io.github.etacassiopeia.rift.VersionCheck;
import io.github.etacassiopeia.rift.error.CommunicationError;
import io.github.etacassiopeia.rift.error.EngineError;
import io.github.etacassiopeia.rift.error.EngineUnavailable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The admin event stream client. These drive a fake that writes real SSE frames, so they pin the
 * framing and the lifecycle — the parts a live engine can't easily be coerced into producing on
 * demand (a malformed frame, a silent connection, a 404). {@code EventStreamIT} proves the rest
 * against a real engine.
 */
@Timeout(20)
class EventStreamTest {

    private static final String HELLO =
            "event: hello\ndata: {\"engineVersion\":\"0.13.6\",\"port\":null,\"seq\":0,\"types\":[\"requests\",\"lifecycle\"]}\n\n";

    private static Rift connect(FakeAdminServer s) {
        return Rift.connect(ConnectOptions.builder(s.baseUri()).versionCheck(VersionCheck.OFF).build());
    }

    private static EventStreamOptions opts() {
        return EventStreamOptions.builder().build();
    }

    private static String requestFrame(long id, int port, String path, String index) {
        return "event: request\nid: " + id + "\ndata: {\"flowId\":\"f1\"," + index + "\"port\":" + port
                + ",\"request\":{\"method\":\"GET\",\"path\":\"" + path + "\"}}\n\n";
    }

    @Test
    void everyEventFamilyIsParsed() throws Exception {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.stream("GET /events", 200, sink -> {
                sink.write(HELLO);
                sink.write(": ping\n\n");
                sink.write(requestFrame(1, 4545, "/a", "\"index\":7,"));
                sink.write("event: imposter\nid: 2\ndata: {\"action\":\"stubsChanged\",\"port\":4545}\n\n");
                sink.write("event: lagged\ndata: {\"missed\":7}\n\n");
                // AllDeleted names no single port — the engine omits it rather than inventing one.
                sink.write("event: imposter\nid: 3\ndata: {\"action\":\"allDeleted\"}\n\n");
            });
            try (Rift rift = connect(s); EventStream stream = rift.events(opts())) {
                List<RiftEvent> events = drain(stream, 5);

                RiftEvent.Hello hello = assertInstanceOf(RiftEvent.Hello.class, events.get(0));
                assertEquals("0.13.6", hello.engineVersion());
                assertEquals(0, hello.seqAtConnect());
                assertEquals(List.of("requests", "lifecycle"), hello.types());
                assertEquals(OptionalInt.empty(), hello.port());
                assertEquals(OptionalLong.empty(), hello.seq(), "hello is not a position in the sequence");

                RiftEvent.RequestRecorded req = assertInstanceOf(RiftEvent.RequestRecorded.class, events.get(1));
                assertEquals(OptionalLong.of(1), req.seq());
                assertEquals(4545, req.port());
                assertEquals(OptionalLong.of(7), req.index(), "the journal index a tail reconciles from");
                assertEquals("f1", req.flowId().orElseThrow());
                assertEquals("/a", req.request().path());

                RiftEvent.ImposterChanged changed = assertInstanceOf(RiftEvent.ImposterChanged.class, events.get(2));
                assertEquals(RiftEvent.ImposterChanged.Action.STUBS_CHANGED, changed.action());
                assertEquals(OptionalInt.of(4545), changed.port());
                assertEquals(OptionalLong.of(2), changed.seq());

                RiftEvent.Lagged lagged = assertInstanceOf(RiftEvent.Lagged.class, events.get(3));
                assertEquals(7, lagged.missed());

                RiftEvent.ImposterChanged all = assertInstanceOf(RiftEvent.ImposterChanged.class, events.get(4));
                assertEquals(RiftEvent.ImposterChanged.Action.ALL_DELETED, all.action());
                assertEquals(OptionalInt.empty(), all.port(), "an engine-wide event has no port to report");
            }
        }
    }

    @Test
    void aHeartbeatIsConsumedNotSurfaced() throws Exception {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.stream("GET /events", 200, sink -> {
                sink.write(": ping\n\n");
                sink.write(": ping\n\n");
                sink.write(HELLO);
            });
            try (Rift rift = connect(s); EventStream stream = rift.events(opts())) {
                // A consumer must never see the keepalive; it exists to prove the socket is alive.
                assertInstanceOf(RiftEvent.Hello.class, drain(stream, 1).get(0));
            }
        }
    }

    @Test
    void aMultiLineDataFieldIsConcatenated() throws Exception {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.stream("GET /events", 200, sink -> sink.write(
                    "event: hello\ndata: {\"engineVersion\":\"0.13.6\",\n"
                            + "data: \"port\":null,\"seq\":3,\"types\":[]}\n\n"));
            try (Rift rift = connect(s); EventStream stream = rift.events(opts())) {
                RiftEvent.Hello hello = assertInstanceOf(RiftEvent.Hello.class, drain(stream, 1).get(0));

                assertEquals(3, hello.seqAtConnect(), "the frame's data spans two lines and is one JSON object");
            }
        }
    }

    @Test
    void anUnknownEventTypeIsSkippedButAMalformedKnownOneIsLoud() throws Exception {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.stream("GET /events", 200, sink -> {
                // Forward compatibility: the engine may grow families this client predates.
                sink.write("event: somethingNew\nid: 9\ndata: {\"whatever\":true}\n\n");
                sink.write(HELLO);
            });
            try (Rift rift = connect(s); EventStream stream = rift.events(opts())) {
                assertInstanceOf(RiftEvent.Hello.class, drain(stream, 1).get(0));
            }
        }
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.stream("GET /events", 200, sink ->
                    sink.write("event: request\nid: 1\ndata: {not json at all\n\n"));
            try (Rift rift = connect(s); EventStream stream = rift.events(opts())) {
                Iterator<RiftEvent> it = stream.iterator();

                // A family we DO know, whose payload we cannot read, is a broken engine or proxy —
                // skipping it would silently drop an event the caller is counting on.
                assertThrows(CommunicationError.class, it::hasNext);
            }
        }
    }

    @Test
    void closeEndsIterationGracefully() throws Exception {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.stream("GET /events", 200, sink -> {
                sink.write(HELLO);
                // Hold the stream open until the client leaves, so close() is what ends it.
                while (!sink.clientGone()) {
                    Thread.sleep(20);
                    sink.write(": ping\n\n");
                }
            });
            try (Rift rift = connect(s)) {
                EventStream stream = rift.events(opts());
                Iterator<RiftEvent> it = stream.iterator();
                assertInstanceOf(RiftEvent.Hello.class, it.next());

                stream.close();

                assertFalse(it.hasNext(), "a closed stream ends; it does not throw");
                stream.close();  // idempotent
            }
        }
    }

    @Test
    void aServerThatDisconnectsIsLoudNotAQuietEnd() throws Exception {
        try (FakeAdminServer s = new FakeAdminServer()) {
            // The handler returns, so the engine drops the connection while the client still wants it.
            s.stream("GET /events", 200, sink -> sink.write(HELLO));
            try (Rift rift = connect(s); EventStream stream = rift.events(opts())) {
                Iterator<RiftEvent> it = stream.iterator();
                assertInstanceOf(RiftEvent.Hello.class, it.next());

                // Ending quietly here would be indistinguishable from "nothing is happening", and a
                // tail cannot tell those apart — so a dropped stream throws and the caller reconciles.
                assertThrows(EngineUnavailable.class, it::hasNext);
            }
        }
    }

    @Test
    void aHeartbeatKeepsAnEventlessStreamAlivePastTheIdleTimeout() throws Exception {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.stream("GET /events", 200, sink -> {
                sink.write(HELLO);
                // Alive, but with nothing to say — the steady state of a tail on an imposter nobody
                // is calling. Only the heartbeat separates it from a connection that died.
                for (int i = 0; i < 12 && !sink.clientGone(); i++) {
                    Thread.sleep(50);
                    sink.write(": ping\n\n");
                }
                sink.write(requestFrame(1, 4545, "/finally", ""));
            });
            try (Rift rift = connect(s);
                 EventStream stream = rift.events(
                         EventStreamOptions.builder().idleTimeout(Duration.ofMillis(200)).build())) {
                Iterator<RiftEvent> it = stream.iterator();
                assertInstanceOf(RiftEvent.Hello.class, it.next());

                // 600ms of heartbeats against a 200ms idle timeout: an idle clock that measured
                // events rather than bytes would have killed this stream three times over.
                assertInstanceOf(RiftEvent.RequestRecorded.class, it.next());
            }
        }
    }

    @Test
    void aFieldOfTheWrongTypeIsLoudNotDefaulted() throws Exception {
        try (FakeAdminServer s = new FakeAdminServer()) {
            // port is how a consumer routes the event; silently reading it as 0 would attribute the
            // request to an imposter that does not exist, and nothing downstream could tell.
            s.stream("GET /events", 200, sink -> sink.write(
                    "event: request\nid: 1\ndata: {\"port\":\"not-a-number\","
                            + "\"request\":{\"method\":\"GET\",\"path\":\"/a\"}}\n\n"));
            try (Rift rift = connect(s); EventStream stream = rift.events(opts())) {
                Iterator<RiftEvent> it = stream.iterator();

                CommunicationError e = assertThrows(CommunicationError.class, it::hasNext);
                assertTrue(e.getMessage().contains("port"), e.getMessage());
            }
        }
        try (FakeAdminServer s = new FakeAdminServer()) {
            // A lagged event whose count defaulted to 0 would report a hole with nothing in it.
            s.stream("GET /events", 200, sink -> sink.write("event: lagged\ndata: {}\n\n"));
            try (Rift rift = connect(s); EventStream stream = rift.events(opts())) {
                assertThrows(CommunicationError.class, () -> stream.iterator().hasNext());
            }
        }
    }

    @Test
    void silenceBeyondTheIdleTimeoutIsADeadConnection() throws Exception {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.stream("GET /events", 200, sink -> {
                sink.write(HELLO);
                Thread.sleep(5_000);   // no data, and no heartbeat either
            });
            try (Rift rift = connect(s);
                 EventStream stream = rift.events(
                         EventStreamOptions.builder().idleTimeout(Duration.ofMillis(300)).build())) {
                Iterator<RiftEvent> it = stream.iterator();
                assertInstanceOf(RiftEvent.Hello.class, it.next());

                assertThrows(EngineUnavailable.class, it::hasNext);
            }
        }
    }

    @Test
    void a404MeansThisEngineCannotStreamAtAll() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("GET /events", 404, "{\"errors\":[{\"code\":\"404\",\"message\":\"Not Found\"}]}");
            try (Rift rift = connect(s)) {
                // Indistinguishable from the embedded transport's answer on purpose: both mean poll.
                assertThrows(UnsupportedOperationException.class, () -> rift.events(opts()));
            }
        }
    }

    @Test
    void aRejectedConnectFailsUpFrontNotMidIteration() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("GET /events", 401, "{\"errors\":[{\"code\":\"401\",\"message\":\"bad api key\"}]}");
            try (Rift rift = connect(s)) {
                EngineError e = assertThrows(EngineError.class, () -> rift.events(opts()));
                assertTrue(e.getMessage().contains("bad api key"), e.getMessage());
            }
        }
    }

    @Test
    void theFiltersAreRenderedOntoTheConnectUrl() throws Exception {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.stream("GET /events", 200, sink -> sink.write(HELLO));
            try (Rift rift = connect(s);
                 EventStream stream = rift.events(EventStreamOptions.builder()
                         .types(EventStreamOptions.EventType.REQUESTS)
                         .port(4545)
                         .match(MatchClause.flowId("tenant-a"))
                         .build())) {
                drain(stream, 1);

                String path = s.received().stream().map(FakeAdminServer.Received::path)
                        .filter(p -> p.startsWith("/events")).findFirst().orElseThrow();
                assertEquals("/events?types=requests&port=4545&match=flow_id%3Dtenant-a", path);
            }
        }
    }

    @Test
    void theStreamIsSingleUseAndRefusesToBeReopened() throws Exception {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.stream("GET /events", 200, sink -> {
                sink.write(HELLO);
                while (!sink.clientGone()) {
                    Thread.sleep(20);
                    sink.write(": ping\n\n");
                }
            });
            try (Rift rift = connect(s)) {
                EventStream stream = rift.events(opts());
                assertTrue(stream.iterator() == stream.iterator(), "a live stream has no start to return to");
                stream.close();
                assertThrows(IllegalStateException.class, stream::iterator);
            }
        }
    }

    private static List<RiftEvent> drain(EventStream stream, int count) {
        List<RiftEvent> out = new ArrayList<>();
        Iterator<RiftEvent> it = stream.iterator();
        while (out.size() < count && it.hasNext()) {
            out.add(it.next());
        }
        assertEquals(count, out.size(), "expected " + count + " events, got " + out);
        return out;
    }
}
