package io.github.etacassiopeia.rift.dsl;

import io.github.etacassiopeia.rift.model.RiftFaultConfig;
import io.github.etacassiopeia.rift.model.RiftLatencyFault;

import java.time.Duration;
import java.util.Optional;

/**
 * A connection-level fault, for use with {@link RiftDsl#fault(Fault)}: the raw TCP-level failure
 * modes Mountebank's {@code {"fault": "..."}} stub response supports.
 *
 * <p>{@link #latencySpike(Duration)} is a separate, non-constant factory: it does not describe a
 * connection fault at all but a {@code _rift} latency-injection response (a normal response,
 * delayed by the given duration), so it returns a {@link ResponseSpec} directly rather than a
 * {@code Fault} value.
 */
public enum Fault {

    /** The connection is reset (RST) as soon as the request is matched. */
    CONNECTION_RESET_BY_PEER("CONNECTION_RESET_BY_PEER"),

    /** Random bytes are written to the socket, then the connection is closed. */
    RANDOM_DATA_THEN_CLOSE("RANDOM_DATA_THEN_CLOSE");

    private final String wireValue;

    Fault(String wireValue) {
        this.wireValue = wireValue;
    }

    /** The wire string this fault serializes as, e.g. {@code "CONNECTION_RESET_BY_PEER"}. */
    String wireValue() {
        return wireValue;
    }

    /**
     * A response delayed by {@code duration} before being delivered, modeled as a {@code _rift}
     * latency fault with probability 1.0 and a fixed {@code ms} delay.
     */
    public static ResponseSpec latencySpike(Duration duration) {
        RiftLatencyFault latency = new RiftLatencyFault(
                RiftLatencyFault.DEFAULT_PROBABILITY, 0, 0, Optional.of(duration.toMillis()));
        RiftFaultConfig config = new RiftFaultConfig(Optional.of(latency), Optional.empty(), Optional.empty());
        return ResponseSpec.withLatencyFault(config);
    }
}
