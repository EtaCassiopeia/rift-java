package io.github.etacassiopeia.rift;

import java.util.List;
import java.util.OptionalLong;

/**
 * One page of recorded requests plus the journal cursor that arrived with it — the primitive behind
 * a correct request tail (rift#603).
 *
 * <p>Tailing by tracking an offset into {@link Imposter#recorded()} looks equivalent and is not:
 * array positions shift under the 10k retention cap and under {@code DELETE savedRequests}, so an
 * offset silently skips or replays entries. The engine assigns every recorded request a stable,
 * 1-based, per-port index instead, and reports the cursor per <em>response</em> rather than per
 * entry — which is why the cursor lives here and {@link RecordedRequest} has no index of its own.
 *
 * <p>The canonical tail: {@link Imposter#recordedPage()} once for a baseline, then
 * {@link Imposter#recordedSince(long)} on an interval, passing {@link #nextIndex()} back verbatim
 * each time and re-baselining when {@link #truncated()} is set.
 *
 * @param requests  the entries in this page, oldest first
 * @param nextIndex the cursor to pass as the next {@code since}, or empty when the engine did not
 *                  report one — an older engine, a journal backend without stable indices, or a
 *                  degraded partial read, which are deliberately indistinguishable because the
 *                  caller's response to all three is the same: <b>do not advance</b>, keep the
 *                  cursor you hold and poll again. A present {@code 0} is a real cursor meaning
 *                  nothing has been recorded yet — absence is the only "unsupported" signal.
 * @param truncated whether retention evicted entries the caller had not seen, leaving a hole in its
 *                  view. Re-baseline with {@link Imposter#recordedPage()}. This is a signal, not an
 *                  error: the entries in this page are still real. Deleting data you asked to
 *                  delete is not a hole, so a cursor held across a clear is never flagged.
 */
public record RecordedPage(List<RecordedRequest> requests, OptionalLong nextIndex, boolean truncated) {

    public RecordedPage {
        requests = List.copyOf(requests);
    }
}
