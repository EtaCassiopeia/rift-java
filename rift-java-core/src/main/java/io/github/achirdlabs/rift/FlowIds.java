package io.github.achirdlabs.rift;

import java.util.Objects;

/**
 * Validates caller-supplied flow ids at the facade boundary (#153). A blank flow id is never the
 * engine's default flow — it routes to a distinct, silently-wrong partition (and on the flow-state
 * DELETE path a destructive misroute), so it is rejected here rather than sent to the wire.
 */
final class FlowIds {

    private FlowIds() {}

    /** A caller-supplied flow id: non-null, non-blank, passed through verbatim otherwise. */
    static String require(String flowId) {
        Objects.requireNonNull(flowId, "flowId");
        if (flowId.isBlank()) {
            throw new IllegalArgumentException("flowId must not be blank");
        }
        return flowId;
    }
}
