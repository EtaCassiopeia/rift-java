package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonNumber;
import io.github.etacassiopeia.rift.json.JsonString;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void proxyPreservesSiblingKeys() {
        // #62: a proxy response with co-present top-level keys (_behaviors, unknown/future) must not
        // drop them — they round-trip via Response.Proxy.extra.
        Stub stub = Stub.fromJson("""
                {"predicates": [], "responses": [
                  {"proxy": {"to": "http://up"}, "_behaviors": {"wait": 100}, "someFutureKey": "value"}]}
                """);
        Response.Proxy proxy = assertInstanceOf(Response.Proxy.class, stub.responses().get(0));
        assertEquals("http://up", proxy.proxy().to());
        assertTrue(proxy.extra().containsKey("_behaviors"));
        assertEquals("value", ((JsonString) proxy.extra().get("someFutureKey")).value());

        Response.Proxy reparsed = assertInstanceOf(Response.Proxy.class,
                Stub.fromJson(stub.toJson()).responses().get(0));
        assertTrue(reparsed.extra().containsKey("_behaviors"));
        assertEquals("value", ((JsonString) reparsed.extra().get("someFutureKey")).value());
    }

    @Test
    void injectPreservesSiblingKeys() {
        // #62: an inject response with a co-present unknown key must not drop it.
        Stub stub = Stub.fromJson("""
                {"predicates": [], "responses": [
                  {"inject": "function(){}", "repeat": 3}]}
                """);
        Response.Inject inject = assertInstanceOf(Response.Inject.class, stub.responses().get(0));
        assertEquals("function(){}", inject.script());
        assertTrue(inject.extra().containsKey("repeat"));

        Response.Inject reparsed = assertInstanceOf(Response.Inject.class,
                Stub.fromJson(stub.toJson()).responses().get(0));
        assertEquals("function(){}", reparsed.script());
        assertTrue(reparsed.extra().containsKey("repeat"));
    }

    @Test
    void faultPreservesSiblingKeys() {
        // #62: a fault response with a co-present unknown key must not drop it.
        Stub stub = Stub.fromJson("""
                {"predicates": [], "responses": [
                  {"fault": "CONNECTION_RESET_BY_PEER", "someFutureKey": "value"}]}
                """);
        Response.Fault fault = assertInstanceOf(Response.Fault.class, stub.responses().get(0));
        assertEquals("CONNECTION_RESET_BY_PEER", fault.fault());
        assertEquals("value", ((JsonString) fault.extra().get("someFutureKey")).value());

        Response.Fault reparsed = assertInstanceOf(Response.Fault.class,
                Stub.fromJson(stub.toJson()).responses().get(0));
        assertEquals("CONNECTION_RESET_BY_PEER", reparsed.fault());
        assertEquals("value", ((JsonString) reparsed.extra().get("someFutureKey")).value());
    }

    @Test
    void proxyPreservesRiftSibling() {
        // The issue's own example: a `_rift` sibling on a proxy (special-cased for is/script kinds)
        // must round-trip via extra like any other sibling, not be dropped.
        Stub stub = Stub.fromJson("""
                {"predicates": [], "responses": [
                  {"proxy": {"to": "http://up"}, "_rift": {"templated": true}}]}
                """);
        Response.Proxy proxy = assertInstanceOf(Response.Proxy.class, stub.responses().get(0));
        assertTrue(proxy.extra().containsKey("_rift"));

        Response.Proxy reparsed = assertInstanceOf(Response.Proxy.class,
                Stub.fromJson(stub.toJson()).responses().get(0));
        assertTrue(reparsed.extra().containsKey("_rift"));
    }

    @Test
    void proxyInjectFaultRejectModeledKeyInExtra() {
        // The compact constructors reject their own modeled key sneaking into extra (a precedence
        // ambiguity), matching IsResponse/ProxyResponse — see UnknownFieldPreservationTest.
        assertThrows(WireFormatException.class,
                () -> new Response.Proxy(new ProxyResponse("http://up"), Map.of("proxy", JsonNumber.of(1))));
        assertThrows(WireFormatException.class,
                () -> new Response.Inject("function(){}", Map.of("inject", JsonNumber.of(1))));
        assertThrows(WireFormatException.class,
                () -> new Response.Fault("CONNECTION_RESET_BY_PEER", Map.of("fault", JsonNumber.of(1))));
    }

    @Test
    void bareProxyInjectFaultHaveEmptyExtra() {
        // Regression: the common no-sibling case carries an empty extra and is otherwise unchanged.
        Stub stub = Stub.fromJson("""
                {"predicates": [], "responses": [
                  {"proxy": {"to": "http://up"}},
                  {"inject": "function(){}"},
                  {"fault": "CONNECTION_RESET_BY_PEER"}]}
                """);
        assertTrue(assertInstanceOf(Response.Proxy.class, stub.responses().get(0)).extra().isEmpty());
        assertTrue(assertInstanceOf(Response.Inject.class, stub.responses().get(1)).extra().isEmpty());
        assertTrue(assertInstanceOf(Response.Fault.class, stub.responses().get(2)).extra().isEmpty());
    }
}
