package io.github.etacassiopeia.rift.embedded;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-unit gate for the {@code rift_set_scenario_state} JSON payload — {@code {"state","flowId"?}} —
 *  without touching the native runtime. */
class FfiCallsScenarioBodyTest {

    @Test
    void defaultFlowOmitsFlowId() {
        String json = FfiCalls.scenarioStateBody("filled", Optional.empty());
        assertTrue(json.contains("\"state\":\"filled\""), json);
        assertFalse(json.contains("flowId"), json);
    }

    @Test
    void flowScopedCarriesFlowId() {
        String json = FfiCalls.scenarioStateBody("filled", Optional.of("flow-1"));
        assertTrue(json.contains("\"state\":\"filled\""), json);
        assertTrue(json.contains("\"flowId\":\"flow-1\""), json);
    }

    @Test
    void flowIdIsJsonEscaped() {
        String json = FfiCalls.scenarioStateBody("s", Optional.of("a\"b"));
        assertEquals("{\"state\":\"s\",\"flowId\":\"a\\\"b\"}", json);
    }
}
