package io.github.etacassiopeia.rift.dsl;

import io.github.etacassiopeia.rift.model.Imposter;
import io.github.etacassiopeia.rift.model.Imposters;
import io.github.etacassiopeia.rift.model.Response;
import io.github.etacassiopeia.rift.model.Stub;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static io.github.etacassiopeia.rift.dsl.RiftDsl.eq;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.imposter;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.okJson;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.onGet;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.proxyTo;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.scenario;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * End-to-end integration of the sub-builders into a full imposter: a proxy response wired into a
 * stub, and a scenario FSM's stubs wired into an imposter — the two integration points the
 * per-construct unit tests build only in isolation. Each asserts the typed model and that the
 * imposter serializes and re-parses stably.
 */
class DslIntegrationTest {

    @Test
    void proxyResponseWiredIntoAStubSerializesStably() {
        Imposter imposter = imposter("proxy-api")
                .port(5000)
                .stub(onGet("/upstream")
                        .willReturn(proxyTo("http://backend:8080").proxyAlways().injectHeader("X-From", "rift")))
                .build();

        Stub stub = imposter.stubs().get(0);
        Response response = stub.responses().get(0);
        Response.Proxy proxy = assertInstanceOf(Response.Proxy.class, response);
        assertEquals("http://backend:8080", proxy.proxy().to());
        assertEquals("proxyAlways", proxy.proxy().mode());
        assertEquals("rift", proxy.proxy().injectHeaders().get("X-From"));

        // Round-trips through the codec: serialize then re-parse yields an equal model.
        Imposters wrapper = new Imposters(List.of(imposter));
        assertEquals(wrapper, Imposters.fromJson(wrapper.toJson()));
    }

    @Test
    void scenarioStubsWiredIntoAnImposterCarryStatesAndSerializeStably() {
        List<Stub> scenarioStubs = scenario("login-flow")
                .startingAt("start")
                .when("start", onGet("/step1")).respond(okJson("{\"step\":1}")).goTo("stepped")
                .when("stepped", onGet("/step2")).respond(okJson("{\"step\":2}")).goTo("done")
                .stubs();

        Imposter imposter = imposter("fsm").port(6000).stub(scenarioStubs).build();

        assertEquals(2, imposter.stubs().size());
        Stub first = imposter.stubs().get(0);
        assertEquals("login-flow", first.scenarioName().orElseThrow());
        // The transition out of the START state carries no requiredScenarioState guard: the engine's
        // flow-state is unset initially, so "being at start" means "no required-state predicate".
        assertEquals(Optional.empty(), first.requiredScenarioState());
        assertEquals("stepped", first.newScenarioState().orElseThrow());
        Stub second = imposter.stubs().get(1);
        assertEquals("stepped", second.requiredScenarioState().orElseThrow());
        assertEquals("done", second.newScenarioState().orElseThrow());

        Imposters wrapper = new Imposters(List.of(imposter));
        assertEquals(wrapper, Imposters.fromJson(wrapper.toJson()));
    }

    @Test
    void eqIsAStaticImportableAliasForEquals() {
        // eq(...) and the qualified RiftDsl.equals(...) must produce the same matcher/model.
        Imposter viaEq = imposter("m").stub(onGet("/x").withHeader("Accept", eq("application/json"))
                .willReturn(okJson("{}"))).build();
        Imposter viaEquals = imposter("m").stub(onGet("/x").withHeader("Accept", RiftDsl.equals("application/json"))
                .willReturn(okJson("{}"))).build();
        assertEquals(viaEquals, viaEq);
    }
}
