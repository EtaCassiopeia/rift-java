package io.github.etacassiopeia.rift.transport;

import io.github.etacassiopeia.rift.EventStream;
import io.github.etacassiopeia.rift.RecordedRequest;
import io.github.etacassiopeia.rift.RiftEvent;
import io.github.etacassiopeia.rift.error.CommunicationError;
import io.github.etacassiopeia.rift.error.EngineUnavailable;
import io.github.etacassiopeia.rift.json.JsonNumber;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads an SSE body into {@link RiftEvent}s, one reader thread per stream feeding the iterator over
 * a bounded queue.
 *
 * <p>The thread is the point, not an accident. The engine buffers 16 frames per client and then lets
 * its broadcast bus lag deliberately — a slow reader is expected to show up as TCP backpressure, and
 * the engine answers it with a {@code lagged} event rather than growing without bound. A bounded
 * queue whose producer blocks reproduces that faithfully. The alternative — handing the body to the
 * shared {@code HttpClient} executor — would park a pool thread every other admin call also needs,
 * turning one slow consumer into a stall across the whole client. An unbounded queue would trade the
 * engine's honest {@code lagged} for a quiet heap climb.
 *
 * <p>The reader is a daemon and exits on close, end of body, or error; nothing outlives the stream.
 */
final class SseEventStream implements EventStream {

    /** Deep enough to absorb a burst, shallow enough that a stalled consumer becomes backpressure. */
    private static final int QUEUE_CAPACITY = 64;

    /** What the reader hands the iterator: an event, the end of the body, or the failure that ended it. */
    private sealed interface Signal {
        record Event(RiftEvent event) implements Signal {}

        /** The caller closed the stream — the one ending that is not a failure. */
        record Ended() implements Signal {}

        record Failed(RuntimeException cause) implements Signal {}
    }

    /** How often the iterator wakes to re-check liveness while waiting; bounded so it stays cheap. */
    private static final long POLL_GRANULARITY_MS = 200;

    private final BlockingQueue<Signal> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    /**
     * When the socket last produced anything at all — including a heartbeat, which produces no
     * event. Liveness is a property of the connection, not of the event flow: an imposter nobody is
     * calling is a silent stream and a healthy one, and only the heartbeat can tell them apart.
     */
    private volatile long lastActivityNanos = System.nanoTime();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final InputStream body;
    private final Thread reader;
    private final Duration idleTimeout;
    private final URI uri;
    private final EventIterator iterator = new EventIterator();

    SseEventStream(InputStream body, URI uri, Duration idleTimeout) {
        this.body = body;
        this.uri = uri;
        this.idleTimeout = idleTimeout;
        this.reader = new Thread(this::read, "rift-events-" + uri.getPort());
        this.reader.setDaemon(true);
        this.reader.start();
    }

    @Override
    public Iterator<RiftEvent> iterator() {
        if (closed.get()) {
            throw new IllegalStateException("this EventStream is closed");
        }
        return iterator;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        // Closing the body is what unblocks the reader if it is parked on a socket read; the reader
        // then sees `closed` and exits without reporting the failure it just caused.
        try {
            body.close();
        } catch (IOException ignored) {
            // Already gone — closing is a request to release, not an operation that can fail usefully.
        }
        reader.interrupt();
        // Drop the backlog the caller will never read, then hand the iterator its ending. clear()
        // alone would leave a consumer parked in poll() until the idle timeout elapsed.
        queue.clear();
        queue.offer(new Signal.Ended());
    }

    private void read() {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            Frame frame = new Frame();
            String line;
            while ((line = lines.readLine()) != null) {
                lastActivityNanos = System.nanoTime();
                if (closed.get()) {
                    return;
                }
                if (line.isEmpty()) {
                    frame.dispatch(this::put);
                    frame = new Frame();
                } else {
                    frame.accept(line);
                }
            }
            if (!closed.get()) {
                // The body ended while the caller still wanted events. Only close() ends a stream
                // quietly; anything else is a connection that went away and must say so.
                put(new Signal.Failed(new EngineUnavailable(
                        "the rift admin event stream at " + uri + " was ended by the engine")));
            }
        } catch (IOException e) {
            if (!closed.get()) {
                put(new Signal.Failed(new EngineUnavailable("the rift admin event stream at " + uri + " dropped", e)));
            }
        } catch (RuntimeException e) {
            if (!closed.get()) {
                put(new Signal.Failed(e));
            }
        }
    }

    /** Blocks when the consumer is behind — the backpressure the engine is designed to see. */
    private void put(Signal signal) {
        try {
            queue.put(signal);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private final class EventIterator implements Iterator<RiftEvent> {

        private RiftEvent next;
        private boolean done;

        @Override
        public boolean hasNext() {
            if (done) {
                return false;
            }
            if (next != null) {
                return true;
            }
            Signal signal = take();
            if (signal instanceof Signal.Event event) {
                next = event.event();
                return true;
            }
            done = true;
            if (signal instanceof Signal.Failed failed) {
                throw failed.cause();
            }
            // Signal.Ended — the stream was closed.
            return false;
        }

        @Override
        public RiftEvent next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            RiftEvent event = next;
            next = null;
            return event;
        }

        private Signal take() {
            long idleNanos = idleTimeout.toNanos();
            long granularity = Math.max(1, Math.min(idleTimeout.toMillis(), POLL_GRANULARITY_MS));
            while (true) {
                if (closed.get()) {
                    return new Signal.Ended();
                }
                Signal signal;
                try {
                    signal = queue.poll(granularity, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new Signal.Failed(
                            new EngineUnavailable("interrupted while reading the rift admin event stream", e));
                }
                // A close() that raced an in-flight event wins: the caller asked to stop.
                if (closed.get()) {
                    return new Signal.Ended();
                }
                if (signal != null) {
                    return signal;
                }
                if (System.nanoTime() - lastActivityNanos > idleNanos) {
                    // Not "no events" — no bytes. The engine heartbeats every 15s, so a connection
                    // this quiet is gone without having said so.
                    return new Signal.Failed(new EngineUnavailable(
                            "the rift admin event stream at " + uri + " went silent for " + idleTimeout
                                    + " (no events and no heartbeat)"));
                }
            }
        }
    }

    /** One SSE frame under construction: {@code event:}/{@code data:}/{@code id:} until a blank line. */
    private static final class Frame {

        private String event;
        private final StringBuilder data = new StringBuilder();
        private OptionalLong id = OptionalLong.empty();
        private boolean any;

        void accept(String line) {
            if (line.startsWith(":")) {
                // A comment — the heartbeat. It proves the socket is alive and is never surfaced.
                return;
            }
            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1).stripLeading();
            switch (field) {
                case "event" -> {
                    event = value;
                    any = true;
                }
                case "data" -> {
                    // Multi-line data concatenates; the spec joins with newlines, and JSON tolerates it.
                    data.append(value);
                    any = true;
                }
                case "id" -> {
                    id = parseId(value);
                    any = true;
                }
                default -> {
                    // An unknown field; the SSE spec says ignore it.
                }
            }
        }

        void dispatch(java.util.function.Consumer<Signal> sink) {
            if (!any || event == null) {
                return;
            }
            RiftEvent parsed = parse(event, data.toString(), id);
            if (parsed != null) {
                sink.accept(new Signal.Event(parsed));
            }
        }

        private static OptionalLong parseId(String value) {
            try {
                return OptionalLong.of(Long.parseLong(value.trim()));
            } catch (NumberFormatException e) {
                throw new CommunicationError("the rift admin event stream sent a non-numeric id: \"" + value + "\"", e);
            }
        }

        /** {@code null} for an event family this client does not know — the engine may grow more. */
        private static RiftEvent parse(String event, String data, OptionalLong id) {
            switch (event) {
                case "hello":
                    return hello(body(event, data));
                case "request":
                    return request(body(event, data), id);
                case "imposter":
                    return imposter(body(event, data), id);
                case "lagged":
                            // A Lagged with a defaulted 0 would report a hole with nothing in it — the one
                    // reading that turns a loss signal into an all-clear.
                    return new RiftEvent.Lagged(require(longField(body(event, data), "missed"), "lagged", "missed"));
                default:
                    return null;
            }
        }

        private static JsonObject body(String event, String data) {
            JsonValue parsed;
            try {
                parsed = JsonValue.parse(data);
            } catch (RuntimeException e) {
                // A family we know, whose payload we cannot read, is a broken engine or a proxy
                // rewriting the stream. Skipping it would drop an event the caller is counting on.
                throw new CommunicationError(
                        "the rift admin event stream sent an unparseable '" + event + "' payload", e);
            }
            if (parsed instanceof JsonObject obj) {
                return obj;
            }
            throw new CommunicationError(
                    "the rift admin event stream sent a non-object '" + event + "' payload");
        }

        private static RiftEvent hello(JsonObject data) {  // NOSONAR — shape mirrors the wire
            List<String> types = data.get("types") instanceof io.github.etacassiopeia.rift.json.JsonArray arr
                    ? arr.items().stream().filter(JsonString.class::isInstance)
                            .map(v -> ((JsonString) v).value()).toList()
                    : List.of();
            return new RiftEvent.Hello(
                    require(stringField(data, "engineVersion"), "hello", "engineVersion"),
                    require(longField(data, "seq"), "hello", "seq"),
                    types,
                    intField(data, "port"));
        }

        private static RiftEvent request(JsonObject data, OptionalLong id) {
            JsonValue request = data.get("request");
            if (request == null) {
                throw new CommunicationError("the rift admin event stream sent a 'request' event with no request");
            }
            return new RiftEvent.RequestRecorded(
                    id,
                    // The port is how a consumer routes the event; defaulting it would hand back a
                    // request attributed to imposter 0, which is unrecoverable once produced.
                    require(intField(data, "port"), "request", "port"),
                    longField(data, "index"),
                    stringField(data, "flowId"),
                    RecordedRequest.read(request));
        }

        private static RiftEvent imposter(JsonObject data, OptionalLong id) {
            Optional<String> action = stringField(data, "action");
            if (action.isEmpty()) {
                throw new CommunicationError("the rift admin event stream sent an 'imposter' event with no action");
            }
            RiftEvent.ImposterChanged.Action parsed = action(action.get());
            // An action this client does not know is a newer engine, not a broken one — skip the
            // frame rather than fail the stream, the same way an unknown event family is skipped.
            return parsed == null ? null : new RiftEvent.ImposterChanged(id, parsed, intField(data, "port"));
        }

        private static RiftEvent.ImposterChanged.Action action(String wire) {
            return switch (wire) {
                case "created" -> RiftEvent.ImposterChanged.Action.CREATED;
                case "replaced" -> RiftEvent.ImposterChanged.Action.REPLACED;
                case "stubsChanged" -> RiftEvent.ImposterChanged.Action.STUBS_CHANGED;
                case "deleted" -> RiftEvent.ImposterChanged.Action.DELETED;
                case "allDeleted" -> RiftEvent.ImposterChanged.Action.ALL_DELETED;
                default -> null;
            };
        }

        private static String require(Optional<String> value, String event, String field) {
            return value.orElseThrow(() -> missing(event, field));
        }

        private static long require(OptionalLong value, String event, String field) {
            if (value.isEmpty()) {
                throw missing(event, field);
            }
            return value.getAsLong();
        }

        private static int require(OptionalInt value, String event, String field) {
            if (value.isEmpty()) {
                throw missing(event, field);
            }
            return value.getAsInt();
        }

        private static CommunicationError missing(String event, String field) {
            return new CommunicationError("the rift admin event stream sent a '" + event
                    + "' event whose '" + field + "' is missing or not the expected type");
        }

        private static Optional<String> stringField(JsonObject obj, String name) {
            return obj.get(name) instanceof JsonString s ? Optional.of(s.value()) : Optional.empty();
        }

        private static OptionalLong longField(JsonObject obj, String name) {
            return obj.get(name) instanceof JsonNumber n ? OptionalLong.of(n.asLong()) : OptionalLong.empty();
        }

        private static OptionalInt intField(JsonObject obj, String name) {
            return obj.get(name) instanceof JsonNumber n ? OptionalInt.of((int) n.asLong()) : OptionalInt.empty();
        }
    }
}
