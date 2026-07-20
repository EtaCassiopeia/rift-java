package io.github.achirdlabs.rift.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Response-level {@code _rift.fault} fault injection: a latency range, a fixed-{@code ms} latency,
 * an error fault, and a raw TCP fault — plus the {@code _rift.templated} flag. None of this appears
 * in any corpus fixture; hand-written spec-derived round-trip test (G2 + G3).
 *
 * <p>Latency has two mutually-exclusive wire forms: a fixed {@code ms} delay, or a {@code
 * minMs}/{@code maxMs} range. Serialization emits only the form that was parsed — the fixed form
 * writes {@code {probability, ms}} (no {@code minMs}/{@code maxMs}), the range form writes {@code
 * {probability, minMs, maxMs}} (no {@code ms}) — so both round-trip exactly (issue #56, corpus 04).
 */
class RiftFaultRoundTripTest {

    private static final String LATENCY_RANGE_JSON = """
            {
              "predicates": [{"equals": {"path": "/slow"}}],
              "responses": [
                {
                  "is": {"statusCode": 200, "body": "ok"},
                  "_rift": {
                    "fault": {"latency": {"probability": 0.5, "minMs": 100, "maxMs": 500}},
                    "templated": true
                  }
                }
              ]
            }
            """;

    private static final String LATENCY_FIXED_MS_JSON = """
            {
              "predicates": [{"equals": {"path": "/slow2"}}],
              "responses": [
                {
                  "is": {"statusCode": 200},
                  "_rift": {"fault": {"latency": {"probability": 0.5, "ms": 250}}}
                }
              ]
            }
            """;

    private static final String ERROR_JSON = """
            {
              "predicates": [{"equals": {"path": "/err"}}],
              "responses": [
                {
                  "is": {"statusCode": 200},
                  "_rift": {
                    "fault": {
                      "error": {"probability": 0.5, "status": 500, "body": "boom", "headers": {"X-Err": "1"}}
                    }
                  }
                }
              ]
            }
            """;

    private static final String TCP_JSON = """
            {
              "predicates": [{"equals": {"path": "/tcp"}}],
              "responses": [
                {
                  "is": {"statusCode": 200},
                  "_rift": {"fault": {"tcp": "CONNECTION_RESET_BY_PEER"}}
                }
              ]
            }
            """;

    @Test
    void latencyRangeFaultRoundTrips() {
        RoundTripAssertions.assertRoundTrips(LATENCY_RANGE_JSON, Stub::fromJson, Stub::toJson);
    }

    @Test
    void latencyRangeFaultIsTyped() {
        Stub stub = Stub.fromJson(LATENCY_RANGE_JSON);
        Response.Is is = (Response.Is) stub.responses().get(0);
        RiftResponseExtension rift = is.rift().orElseThrow();
        assertTrue(rift.templated());

        RiftLatencyFault latency = rift.fault().orElseThrow().latency().orElseThrow();
        assertEquals(0.5, latency.probability());
        assertEquals(100L, latency.minMs());
        assertEquals(500L, latency.maxMs());
        assertTrue(latency.ms().isEmpty());
    }

    @Test
    void latencyFixedMsFaultRoundTrips() {
        RoundTripAssertions.assertRoundTrips(LATENCY_FIXED_MS_JSON, Stub::fromJson, Stub::toJson);
    }

    @Test
    void latencyFixedMsFaultIsTyped() {
        Stub stub = Stub.fromJson(LATENCY_FIXED_MS_JSON);
        Response.Is is = (Response.Is) stub.responses().get(0);
        RiftLatencyFault latency = is.rift().orElseThrow().fault().orElseThrow().latency().orElseThrow();
        assertEquals(0.5, latency.probability());
        assertEquals(250L, latency.ms().orElseThrow());
    }

    @Test
    void errorFaultRoundTrips() {
        RoundTripAssertions.assertRoundTrips(ERROR_JSON, Stub::fromJson, Stub::toJson);
    }

    @Test
    void errorFaultIsTyped() {
        Stub stub = Stub.fromJson(ERROR_JSON);
        Response.Is is = (Response.Is) stub.responses().get(0);
        RiftErrorFault error = is.rift().orElseThrow().fault().orElseThrow().error().orElseThrow();
        assertEquals(0.5, error.probability());
        assertEquals(500, error.status());
        assertEquals("boom", error.body().orElseThrow());
        assertEquals("1", error.headers().get("X-Err"));
    }

    private static final String TCP_PROBABILISTIC_JSON = """
            {
              "predicates": [{"equals": {"path": "/tcp"}}],
              "responses": [
                {
                  "is": {"statusCode": 200},
                  "_rift": {"fault": {"tcp": {"probability": 0.1, "type": "CONNECTION_RESET_BY_PEER"}}}
                }
              ]
            }
            """;

    @Test
    void tcpFaultRoundTrips() {
        RoundTripAssertions.assertRoundTrips(TCP_JSON, Stub::fromJson, Stub::toJson);
    }

    @Test
    void tcpFaultIsTyped() {
        Stub stub = Stub.fromJson(TCP_JSON);
        Response.Is is = (Response.Is) stub.responses().get(0);
        RiftTcpFault tcp = is.rift().orElseThrow().fault().orElseThrow().tcp().orElseThrow();
        RiftTcpFault.Bare bare = assertInstanceOf(RiftTcpFault.Bare.class, tcp);
        assertEquals("CONNECTION_RESET_BY_PEER", bare.type());
        assertEquals(1.0, bare.probability());
    }

    @Test
    void probabilisticTcpFaultRoundTrips() {
        RoundTripAssertions.assertRoundTrips(TCP_PROBABILISTIC_JSON, Stub::fromJson, Stub::toJson);
    }

    @Test
    void probabilisticTcpFaultIsTyped() {
        Stub stub = Stub.fromJson(TCP_PROBABILISTIC_JSON);
        Response.Is is = (Response.Is) stub.responses().get(0);
        RiftTcpFault tcp = is.rift().orElseThrow().fault().orElseThrow().tcp().orElseThrow();
        RiftTcpFault.Probabilistic p = assertInstanceOf(RiftTcpFault.Probabilistic.class, tcp);
        assertEquals(0.1, p.probability());
        assertEquals("CONNECTION_RESET_BY_PEER", p.type());
    }

    @Test
    void probabilisticTcpFaultRequiresProbability() {
        // The object form exists solely to carry probability — an object without it is a wire error,
        // not a second spelling of the bare form.
        assertThrows(WireFormatException.class, () -> Stub.fromJson("""
                {"predicates": [], "responses": [
                  {"is": {"statusCode": 200}, "_rift": {"fault": {"tcp": {"type": "CONNECTION_RESET_BY_PEER"}}}}]}
                """));
    }
}
