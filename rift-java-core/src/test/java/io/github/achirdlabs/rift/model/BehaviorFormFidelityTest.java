package io.github.achirdlabs.rift.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine accepts {@code copy} as a single object (not only an array) and {@code wait} as a bare
 * string (a function body / named latency), not only a number/object. The wire model must parse both
 * and round-trip back to the <em>same</em> form the input used — the fidelity the conformance corpus
 * (fixtures 03/14/16/20) depends on. See issue #55.
 */
class BehaviorFormFidelityTest {

    @Test
    void copySingleObjectFormRoundTripsAsAnObject() {
        String json = """
                {"predicates":[{"equals":{"path":"/orders/1"}}],
                 "responses":[{"is":{"statusCode":200},
                   "_behaviors":{"copy":{"from":"path","into":"${id}",
                     "using":{"method":"regex","selector":"/orders/(\\\\d+)"}}}}]}
                """;
        RoundTripAssertions.assertRoundTrips(json, Stub::fromJson, Stub::toJson);

        String out = Stub.fromJson(json).toJson();
        assertTrue(out.contains("\"copy\":{"), () -> "single-object copy must stay an object, was: " + out);
    }

    @Test
    void copyArrayFormStillRoundTripsAsAnArray() {
        String json = """
                {"predicates":[{"equals":{"path":"/orders/1"}}],
                 "responses":[{"is":{"statusCode":200},
                   "_behaviors":{"copy":[{"from":"path","into":"${id}",
                     "using":{"method":"regex","selector":"/orders/(\\\\d+)"}}]}}]}
                """;
        RoundTripAssertions.assertRoundTrips(json, Stub::fromJson, Stub::toJson);

        String out = Stub.fromJson(json).toJson();
        assertTrue(out.contains("\"copy\":["), () -> "array copy must stay an array, was: " + out);
    }

    @Test
    void waitBareStringFormRoundTripsAsAString() {
        String json = """
                {"predicates":[{"equals":{"path":"/slow"}}],
                 "responses":[{"is":{"statusCode":200},
                   "_behaviors":{"wait":"function(){ return 0; }"}}]}
                """;
        RoundTripAssertions.assertRoundTrips(json, Stub::fromJson, Stub::toJson);

        String out = Stub.fromJson(json).toJson();
        assertTrue(out.contains("\"wait\":\"function"), () -> "bare-string wait must stay a string, was: " + out);
    }

    @Test
    void waitFixedNumberFormIsUnchanged() {
        String json = """
                {"predicates":[{"equals":{"path":"/slow"}}],
                 "responses":[{"is":{"statusCode":200},"_behaviors":{"wait":250}}]}
                """;
        RoundTripAssertions.assertRoundTrips(json, Stub::fromJson, Stub::toJson);

        String out = Stub.fromJson(json).toJson();
        assertTrue(out.contains("\"wait\":250"), () -> "numeric wait must stay a number, was: " + out);
    }
}
