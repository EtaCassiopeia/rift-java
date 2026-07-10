package io.github.etacassiopeia.rift.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine accepts a recorded response in "flat" form — top-level {@code statusCode}/{@code
 * headers}/{@code body}/{@code _mode} with no {@code is} wrapper. {@link Response#read} must read it
 * as an {@code Is} response (never lose the fields), and re-serialize it in the canonical {@code
 * is:{}} shape.
 */
class FlatResponseFormTest {

    @Test
    void flatResponseReadAsIsAndWrittenCanonical() {
        Stub stub = Stub.fromJson("""
                {"predicates": [], "responses": [
                  {"statusCode": 201, "headers": {"X-A": "b"}, "body": "created"}
                ]}
                """);

        Response r = stub.responses().get(0);
        assertTrue(r instanceof Response.Is, "flat form must parse as an Is response");
        Response.Is is = (Response.Is) r;
        assertEquals("201", is.is().statusCode());
        assertEquals("created", ((io.github.etacassiopeia.rift.json.JsonString) is.is().body().orElseThrow()).value());
        assertEquals("b", is.is().headers().get("X-A").get(0));

        // Written back canonically, wrapped in `is` (not left flat), and re-parses to the same Is.
        String out = stub.toJson();
        assertTrue(out.contains("\"is\""), "canonical output wraps the response in `is`: " + out);
        Response.Is reparsed = (Response.Is) Stub.fromJson(out).responses().get(0);
        assertEquals("201", reparsed.is().statusCode());
        assertEquals("created", ((io.github.etacassiopeia.rift.json.JsonString) reparsed.is().body().orElseThrow()).value());
        assertEquals("b", reparsed.is().headers().get("X-A").get(0));
    }

    @Test
    void flatBodyOnlyResponsePreservesBody() {
        Stub stub = Stub.fromJson("""
                {"predicates": [], "responses": [{"body": "just-body"}]}
                """);
        Response.Is is = (Response.Is) stub.responses().get(0);
        assertEquals("200", is.is().statusCode());
        assertEquals("just-body", ((io.github.etacassiopeia.rift.json.JsonString) is.is().body().orElseThrow()).value());
    }

    @Test
    void flatHeadersOnlyResponseReadAsIs() {
        Stub stub = Stub.fromJson("""
                {"predicates": [], "responses": [{"headers": {"X-Only": "y"}}]}
                """);
        Response.Is is = (Response.Is) stub.responses().get(0);
        assertEquals("200", is.is().statusCode());
        assertEquals("y", is.is().headers().get("X-Only").get(0));
    }

    @Test
    void flatResponseWithBehaviorsParsesBehaviorsAsSibling() {
        // A flat response that also carries _behaviors must interpret the behavior (not silently fold
        // it into the is object, where the engine would never see it as a behavior).
        Stub stub = Stub.fromJson("""
                {"predicates": [], "responses": [
                  {"statusCode": 200, "body": "hi", "_behaviors": {"wait": 100}}
                ]}
                """);
        Response.Is is = (Response.Is) stub.responses().get(0);
        assertEquals("200", is.is().statusCode());
        assertFalse(is.behaviors().isEmpty(), "the wait behavior must be interpreted, not dropped");
        assertFalse(is.is().extra().containsKey("_behaviors"), "_behaviors must not be folded into the is object");

        // Canonical write: _behaviors is a sibling of `is`, and re-parses to the same behavior.
        Response.Is reparsed = (Response.Is) Stub.fromJson(stub.toJson()).responses().get(0);
        assertFalse(reparsed.behaviors().isEmpty());
        assertEquals("200", reparsed.is().statusCode());
    }

    @Test
    void emptyResponseObjectStaysDefault200Is() {
        Stub stub = Stub.fromJson("""
                {"predicates": [], "responses": [{}]}
                """);
        Response.Is is = (Response.Is) stub.responses().get(0);
        assertEquals("200", is.is().statusCode());
        assertTrue(is.is().body().isEmpty());
    }
}
