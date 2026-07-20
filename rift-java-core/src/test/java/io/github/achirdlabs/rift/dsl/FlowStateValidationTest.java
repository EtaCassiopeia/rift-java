package io.github.achirdlabs.rift.dsl;

import io.github.achirdlabs.rift.model.ImposterDefinition;
import org.junit.jupiter.api.Test;

import static io.github.achirdlabs.rift.dsl.RiftDsl.imposter;
import static io.github.achirdlabs.rift.dsl.RiftDsl.inMemoryFlowState;
import static io.github.achirdlabs.rift.dsl.RiftDsl.ok;
import static io.github.achirdlabs.rift.dsl.RiftDsl.onGet;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #40 — {@code ImposterSpec.build()} fails fast on a flow-state/spaces misconfiguration: a space stub
 * with no header-form {@code flowIdSource} can never match (engine flow-id default is imposter_port).
 */
class FlowStateValidationTest {

    @Test
    void spaceStubWithoutFlowStateThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> imposter("x").port(1).stub(onGet("/a").inSpace("alice").willReturn(ok())).build());
        assertTrue(ex.getMessage().contains("flowIdFromHeader"), ex.getMessage());
        assertTrue(ex.getMessage().contains("imposter_port"),
                "message should explain why (engine flow-id default is imposter_port): " + ex.getMessage());
    }

    @Test
    void spaceStubWithInMemoryButNoHeaderThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> imposter("x").port(1).flowState(inMemoryFlowState())
                        .stub(onGet("/a").inSpace("alice").willReturn(ok())).build());
    }

    @Test
    void spaceStubWithHeaderSourcePasses() {
        assertDoesNotThrow(
                () -> imposter("x").port(1).flowState(inMemoryFlowState().flowIdFromHeader("X-Flow"))
                        .stub(onGet("/a").inSpace("alice").willReturn(ok())).build());
    }

    @Test
    void scenarioStubWithoutFlowStatePasses() {
        // Engine auto-provisions a store for scenario stubs (#514) — no space, so no throw.
        assertDoesNotThrow(
                () -> imposter("x").port(1)
                        .stub(onGet("/a").inScenario("cart").whenScenarioState("empty").willReturn(ok())).build());
    }

    @Test
    void plainDefPasses() {
        assertDoesNotThrow(() -> imposter("x").port(1).stub(onGet("/a").willReturn(ok())).build());
    }

    @Test
    void buildDoesNotInjectFlowState() {
        // D4: no auto-enrichment — the built def carries exactly what the user declared.
        ImposterDefinition def = imposter("x").port(1).stub(onGet("/a").willReturn(ok())).build();
        assertTrue(def.rift().isEmpty() || def.rift().orElseThrow().flowState().isEmpty(),
                "build() must not inject _rift.flowState");
    }
}
