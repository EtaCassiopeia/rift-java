package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #29 — {@code Response.read} must never silently drop data. Two shapes previously lost fields:
 * a {@code _rift} extension alongside flat is-fields (dropped the is-fields as a script-only response),
 * and an object naming no known kind and no flat fields (collapsed to a default 200, dropping every
 * key). Both are now read as an {@code is} response that preserves the data (the {@code _rift}
 * extension, or unknown keys via {@link IsResponse#extra}), consistent with the codebase's
 * unknown-field-preservation contract. The flat form normalizes to the canonical {@code is:{}} shape,
 * so preservation is asserted by value across a round-trip, not by byte-identity.
 */
class ResponseFieldPreservationTest {

    @Test
    void riftWithFlatIsFieldsPreservesData() {
        Stub stub = Stub.fromJson("""
                {"predicates": [], "responses": [
                  {"_rift": {"templated": true}, "statusCode": 201, "body": "important"}]}
                """);
        Response.Is is = assertInstanceOf(Response.Is.class, stub.responses().get(0));
        assertEquals("201", is.is().statusCode());
        assertEquals("important", ((JsonString) is.is().body().orElseThrow()).value());
        assertTrue(is.rift().orElseThrow().templated());

        // The fields survive a full parse -> serialize -> parse cycle (no silent drop).
        Response.Is reparsed = assertInstanceOf(Response.Is.class,
                Stub.fromJson(stub.toJson()).responses().get(0));
        assertEquals("201", reparsed.is().statusCode());
        assertEquals("important", ((JsonString) reparsed.is().body().orElseThrow()).value());
        assertTrue(reparsed.rift().orElseThrow().templated());
    }

    @Test
    void unknownOnlyResponsePreservesFieldsViaExtra() {
        Stub stub = Stub.fromJson("""
                {"predicates": [], "responses": [
                  {"repeat": 3, "someFutureKey": "value"}]}
                """);
        Response.Is is = assertInstanceOf(Response.Is.class, stub.responses().get(0));
        assertTrue(is.is().extra().containsKey("repeat"));
        assertEquals("value", ((JsonString) is.is().extra().get("someFutureKey")).value());

        // The unknown keys survive a full parse -> serialize -> parse cycle.
        Response.Is reparsed = assertInstanceOf(Response.Is.class,
                Stub.fromJson(stub.toJson()).responses().get(0));
        assertTrue(reparsed.is().extra().containsKey("repeat"));
        assertEquals("value", ((JsonString) reparsed.is().extra().get("someFutureKey")).value());
    }

    @Test
    void scriptOnlyRiftStaysRiftScript() {
        // Regression guard: a `_rift` with NO flat is-fields is still a script-only response.
        Stub stub = Stub.fromJson("""
                {"predicates": [], "responses": [
                  {"_rift": {"templated": true}}]}
                """);
        assertInstanceOf(Response.RiftScript.class, stub.responses().get(0));
        assertFalse(stub.responses().get(0) instanceof Response.Is);
    }

    @Test
    void scriptOnlyRiftPreservesSiblingKeys() {
        // A script-only `_rift` (no flat is-fields) stays a RiftScript, but sibling top-level keys
        // (_behaviors, unknown/future keys) must not be silently dropped — they round-trip via extra.
        Stub stub = Stub.fromJson("""
                {"predicates": [], "responses": [
                  {"_rift": {"templated": true}, "_behaviors": {"wait": 100}, "someFutureKey": "value"}]}
                """);
        Response.RiftScript rift = assertInstanceOf(Response.RiftScript.class, stub.responses().get(0));
        assertTrue(rift.extra().containsKey("_behaviors"));
        assertEquals("value", ((JsonString) rift.extra().get("someFutureKey")).value());

        Response.RiftScript reparsed = assertInstanceOf(Response.RiftScript.class,
                Stub.fromJson(stub.toJson()).responses().get(0));
        assertTrue(reparsed.extra().containsKey("_behaviors"));
        assertEquals("value", ((JsonString) reparsed.extra().get("someFutureKey")).value());
    }
}
