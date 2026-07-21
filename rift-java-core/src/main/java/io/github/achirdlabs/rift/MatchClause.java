package io.github.achirdlabs.rift;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One server-side filter clause over an imposter's recorded-request journal or the engine's event
 * stream — the {@code match=} grammar. Clauses AND together; the engine evaluates them, so a
 * filtered read costs only what it returns.
 *
 * <p>Not to be confused with {@link io.github.achirdlabs.rift.verify.RequestMatch}, which is the
 * Mountebank predicate set the engine evaluates for {@code verify}. The two answer different
 * questions and are not interchangeable: {@code RequestMatch} can express {@code jsonPath}, regex or
 * {@code xpath} over a body, while {@code match=} is deliberately four exact-equality forms over a
 * request's identity. There is no general mapping from one to the other.
 *
 * <p>The grammar is closed on purpose, and the engine rejects a clause it cannot parse with a 400
 * rather than serving everything — a filter that silently widened would cross-contaminate the
 * correlated scenarios it exists to separate. This type mirrors that: the only clauses it can build
 * are ones the engine accepts. For the same reason it also rejects clauses the engine would accept
 * but could never match, since a filter that silently returns nothing is the same failure wearing
 * the opposite mask.
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
     * {@return a clause keeping only requests whose method is exactly {@code method}} Matched
     * <em>case-sensitively</em> by the engine, so {@code method("get")} does not select a
     * {@code GET}.
     *
     * <p>Requires a rift engine ≥ 0.15.0. An older engine rejects the clause with a 400 that
     * surfaces as {@link io.github.achirdlabs.rift.error.InvalidDefinition} — it never quietly
     * serves an unfiltered page instead.
     *
     * @throws IllegalArgumentException if {@code method} is not an HTTP token (RFC 9110 §5.6.2)
     */
    static MatchClause method(String method) {
        return new Method(method);
    }

    /**
     * {@return a clause keeping only requests whose path is exactly {@code path}} Compared against
     * the <em>bare</em> path exactly as recorded — still percent-encoded, and with no query string —
     * so a path recorded as {@code /a%20b} is selected by {@code path("/a%20b")}.
     *
     * <p>Requires a rift engine ≥ 0.15.0. An older engine rejects the clause with a 400 that
     * surfaces as {@link io.github.achirdlabs.rift.error.InvalidDefinition} — it never quietly
     * serves an unfiltered page instead.
     *
     * @throws IllegalArgumentException if {@code path} does not start with {@code /}, or carries a
     *                                  query string or fragment — see {@link Path}
     */
    static MatchClause path(String path) {
        return new Path(path);
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

        public Header {
            HttpTokens.require(name, "header name");
            Objects.requireNonNull(value, "header value");
        }
    }

    /** {@code match=flow_id=<Value>} */
    record FlowId(String value) implements MatchClause {
        public FlowId {
            FlowIds.require(value);
        }
    }

    /**
     * {@code match=method=<Verb>}
     *
     * <p>Held to the HTTP token grammar, which is what a method is (RFC 9110 §9). A non-token could
     * appear in no recorded request — the engine could never have parsed such a request line — so
     * the clause would match nothing for the life of the tail without ever erroring. The value is
     * never case-folded: engine comparison is case-sensitive, so coercing it would silently widen a
     * filter that legitimately matches nothing.
     */
    record Method(String value) implements MatchClause {
        public Method {
            HttpTokens.require(value, "method");
        }
    }

    /**
     * {@code match=path=<Path>}
     *
     * <p>Compared against the bare recorded path, so a clause carrying a query string or fragment
     * would be compared whole and never match — the silent-empty twin of a silently-widened filter,
     * and just as unrepresentable here. A recorded path always begins with {@code /}, so anything
     * else (a blank, a relative path, an absolute URL) is likewise a permanent never-matcher, as is
     * a path carrying a character no request target can hold — see {@link #UNRECORDABLE}.
     */
    record Path(String value) implements MatchClause {

        /**
         * The characters an HTTP request target cannot carry, so a recorded path never contains one
         * and a clause containing one matches nothing forever. Deliberately narrow: it is exactly
         * what the engine's URI parser refuses (controls, space, {@code <}, {@code >}, backtick).
         * Everything else printable is genuinely recordable — including {@code "}, {@code {}},
         * {@code []}, {@code |} and raw non-ASCII, all of which real clients send and the parser
         * accepts — so those stay expressible rather than being over-rejected here.
         */
        private static final Pattern UNRECORDABLE = Pattern.compile("[\\x00-\\x20<>`]");

        public Path {
            Objects.requireNonNull(value, "path");
            if (!value.startsWith("/")) {
                throw new IllegalArgumentException(
                        "a path clause must be the bare request path, starting with '/': \"" + value + "\"");
            }
            if (value.contains("?") || value.contains("#")) {
                throw new IllegalArgumentException(
                        "a path clause is compared against the bare path, which carries no query or "
                                + "fragment: \"" + value + "\"");
            }
            if (UNRECORDABLE.matcher(value).find()) {
                throw new IllegalArgumentException(
                        "a path clause is compared against the raw recorded path, which cannot contain a "
                                + "space, control character, '<', '>' or '`' — percent-encode it: \""
                                + value + "\"");
            }
        }
    }
}
