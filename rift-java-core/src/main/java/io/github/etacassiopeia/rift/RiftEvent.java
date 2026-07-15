package io.github.etacassiopeia.rift;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * One event off the engine's admin stream (rift#461). Sealed, so a consumer can switch over it
 * exhaustively.
 *
 * <p>Deliberately not a stream of {@link RecordedRequest}: lifecycle changes and {@link Lagged} are
 * not recorded requests, and flattening them away would lose exactly the signals a tail needs to
 * stay correct. A request-only tail filters to {@link RequestRecorded} at the call site.
 *
 * @see EventStream
 */
public sealed interface RiftEvent {

    /**
     * {@return this event's position in the engine's monotonic sequence — the SSE {@code id:}} A gap
     * between consecutive events means the stream missed some: reconcile with
     * {@link Imposter#recordedSince(long)} rather than assuming continuity.
     *
     * <p>Empty on {@link Hello} and {@link Lagged}, which are stream-level frames rather than
     * positions in it.
     */
    OptionalLong seq();

    /**
     * The opening frame, sent once on connect.
     *
     * @param seqAtConnect the sequence position the stream starts from — everything before it
     *                     happened before you connected, and is only reachable by polling
     * @param port         the port this stream was filtered to, or empty when it spans the engine
     */
    record Hello(String engineVersion, long seqAtConnect, List<String> types, OptionalInt port) implements RiftEvent {

        public Hello {
            types = List.copyOf(types);
        }

        @Override
        public OptionalLong seq() {
            return OptionalLong.empty();
        }
    }

    /**
     * An imposter recorded a request. Requires the imposter to have {@code recordRequests} on — this
     * is a tail of the journal, not a tap of all traffic.
     *
     * @param index the journal index, the same one {@link RecordedPage#nextIndex()} reports for the
     *              polling side (rift#603) — so the last one seen is what you pass to
     *              {@link Imposter#recordedSince(long)} to reconcile. Empty when the journal backend
     *              has no stable indices, which is the same capability probe the polling side uses:
     *              without it, do not try to resume from a position.
     */
    record RequestRecorded(OptionalLong seq, int port, OptionalLong index, Optional<String> flowId,
                           RecordedRequest request) implements RiftEvent {}

    /**
     * An imposter was created, replaced, or deleted.
     *
     * @param port the imposter affected, or empty for an engine-wide event — {@link Action#ALL_DELETED}
     *             names no single port, so there is no port to report rather than a placeholder one
     */
    record ImposterChanged(OptionalLong seq, Action action, OptionalInt port) implements RiftEvent {

        /** What happened to the imposter. An unrecognized action never reaches here — the frame is skipped. */
        public enum Action {
            CREATED, REPLACED, STUBS_CHANGED, DELETED, ALL_DELETED
        }
    }

    /**
     * The engine dropped {@code missed} events before they reached this stream, because this client
     * was not reading fast enough.
     *
     * <p>Not an error, and not fatal — iteration continues. It is the stream telling you your view
     * has a hole, which is the whole point of it being lossy-<em>but-loud</em>: reconcile with
     * {@link Imposter#recordedSince(long)} from the last {@link RequestRecorded#index()} you saw.
     */
    record Lagged(long missed) implements RiftEvent {

        @Override
        public OptionalLong seq() {
            return OptionalLong.empty();
        }
    }
}
