package io.github.etacassiopeia.rift;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One server-side filter clause over an imposter's recorded-request journal or the engine's event
 * stream — the {@code match=} grammar. Clauses AND together; the engine evaluates them, so a
 * filtered read costs only what it returns.
 *
 * <p>Not to be confused with {@link io.github.etacassiopeia.rift.verify.RequestMatch}, which is the
 * Mountebank predicate set the engine evaluates for {@code verify}. The two answer different
 * questions and are not interchangeable: {@code RequestMatch} can express {@code jsonPath}, regex or
 * {@code xpath} over a body, while {@code match=} is deliberately two forms over a request's
 * identity. There is no general mapping from one to the other.
 *
 * <p>The grammar is closed on purpose, and the engine rejects a clause it cannot parse with a 400
 * rather than serving everything — a filter that silently widened would cross-contaminate the
 * correlated scenarios it exists to separate. This type mirrors that: the only clauses it can build
 * are ones the engine accepts.
 */
public sealed interface MatchClause {

    /**
     * {@return a clause keeping only requests carrying {@code name: value}} The header name is
     * matched case-insensitively by the engine; the value exactly.
     *
     * @throws IllegalArgumentException if {@code name} is not a valid header name (an HTTP token,
     *                                  RFC 9110 §5.6.2) — see {@link Header}
     */
    static MatchClause header(String name, String value) {
        return new Header(name, value);
    }

    /**
     * {@return a clause keeping only requests whose engine-resolved flow id is {@code flowId}}
     *
     * @throws IllegalArgumentException if {@code flowId} is blank — a blank id is never the default
     *     flow but a distinct, silently-wrong partition, so it is rejected rather than sent verbatim
     */
    static MatchClause flowId(String flowId) {
        return new FlowId(flowId);
    }

    /**
     * {@code match=header:<Name>=<Value>}
     *
     * <p>The name is held to the HTTP token grammar (RFC 9110 §5.6.2), which rejects more than the
     * engine's own empty-name check does — deliberately. The clause is rendered as
     * {@code header:<Name>=<Value>} and the engine splits it on its <em>first</em> {@code =}, so a
     * name containing {@code =} would not be rejected: it would quietly re-split into a different
     * name and value and filter on something the caller never asked for. A filter that silently
     * means something else is the failure this whole grammar is closed to prevent, so the one input
     * that could cause it is not constructible.
     */
    record Header(String name, String value) implements MatchClause {

        private static final Pattern TOKEN = Pattern.compile("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+");

        public Header {
            Objects.requireNonNull(name, "header name");
            Objects.requireNonNull(value, "header value");
            if (!TOKEN.matcher(name).matches()) {
                throw new IllegalArgumentException(
                        "not a valid header name (RFC 9110 token): \"" + name + "\"");
            }
        }
    }

    /** {@code match=flow_id=<Value>} */
    record FlowId(String value) implements MatchClause {
        public FlowId {
            FlowIds.require(value);
        }
    }
}
