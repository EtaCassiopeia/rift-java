package io.github.etacassiopeia.rift.dsl;

/**
 * A raw TCP-level connection fault, for use with {@link RiftDsl#fault(Fault)} (Mountebank's
 * {@code {"fault": "..."}} stub response) and {@link IsSpec#withTcpFault(Fault)} (a {@code _rift}
 * fault config carrying the same fault name). Each constant's wire name is exactly its {@link
 * #name()} — there is no separate wire-value mapping.
 *
 * <p>Latency injection is not a connection fault at all — see {@link IsSpec#withLatencyFault}.
 */
public enum Fault {

    /** The connection is reset (RST) as soon as the request is matched. */
    CONNECTION_RESET_BY_PEER,

    /** The connection is closed immediately with no response written. */
    EMPTY_RESPONSE,

    /** Random bytes are written to the socket, then the connection is closed. */
    RANDOM_DATA_THEN_CLOSE,

    /** A malformed (non-HTTP-conformant) response chunk is written before the connection closes. */
    MALFORMED_RESPONSE_CHUNK
}
