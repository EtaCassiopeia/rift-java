package io.github.etacassiopeia.rift;

import java.util.Iterator;

/**
 * A live subscription to the engine's admin event stream: iterate it to consume events, close it to
 * stop. Single-use — {@link #iterator()} returns the one iterator, not a fresh view.
 *
 * <p>Iterate from one thread: the iterator is not synchronized, and a live stream has no start to
 * hand a second consumer. {@link #close()} is the exception — it is safe from any thread, which is
 * how you stop a blocked iteration.
 *
 * <p>Iteration blocks: {@code hasNext()} waits for the next event. Three things end it, and they
 * mean different things:
 *
 * <table border="1">
 *   <caption>How iteration ends</caption>
 *   <tr><th>Cause</th><th>Behaviour</th></tr>
 *   <tr><td>{@link #close()}</td><td>{@code hasNext()} returns {@code false} — a graceful end</td></tr>
 *   <tr><td>The engine disconnects, or goes silent past the idle timeout</td>
 *       <td>{@code hasNext()} throws {@link io.github.etacassiopeia.rift.error.EngineUnavailable}</td></tr>
 *   <tr><td>The engine refuses the connect (bad key, bad filter)</td>
 *       <td>{@link Rift#events} throws, before you get a stream at all</td></tr>
 * </table>
 *
 * <p>Death is loud on purpose. Ending quietly on a dropped connection would look identical to
 * "nothing is happening", and a tail cannot tell those apart — so a broken stream throws and the
 * caller reconciles.
 *
 * <p><b>The stream never replays and never reconnects itself.</b> It is one connection's worth of
 * events; polling remains the source of truth. The canonical loop:
 *
 * <pre>{@code
 * long cursor = imposter.recordedPage().nextIndex().orElse(0);   // baseline first
 * try (EventStream events = rift.events(EventStreamOptions.builder().port(4545).build())) {
 *     for (RiftEvent event : events) {
 *         if (event instanceof RiftEvent.RequestRecorded r) {
 *             cursor = r.index().orElse(cursor);
 *             handle(r.request());
 *         } else if (event instanceof RiftEvent.Lagged) {
 *             cursor = reconcile(cursor);   // imposter.recordedSince(cursor)
 *         }
 *     }
 * } catch (EngineUnavailable dead) {
 *     cursor = reconcile(cursor);           // then reconnect, if you want to
 * }
 * }</pre>
 *
 * Reconnect policy is deliberately the caller's: a retry schedule belongs to whatever is driving
 * this loop, not baked into the connection.
 */
public interface EventStream extends AutoCloseable, Iterable<RiftEvent> {

    /**
     * {@return this stream's iterator} Blocking, and single-use: the same iterator every call, since
     * a live stream has no start to return to.
     *
     * @throws IllegalStateException if called after {@link #close()}
     */
    @Override
    Iterator<RiftEvent> iterator();

    /** Stops the stream and releases the connection. Idempotent, and unblocks a waiting {@code hasNext()}. */
    @Override
    void close();
}
