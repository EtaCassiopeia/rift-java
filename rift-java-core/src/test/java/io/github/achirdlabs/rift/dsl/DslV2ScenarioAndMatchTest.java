package io.github.achirdlabs.rift.dsl;

import io.github.achirdlabs.rift.model.Predicate;
import io.github.achirdlabs.rift.model.PredicateOperation;
import io.github.achirdlabs.rift.verify.RequestMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.achirdlabs.rift.dsl.RiftDsl.created;
import static io.github.achirdlabs.rift.dsl.RiftDsl.ok;
import static io.github.achirdlabs.rift.dsl.RiftDsl.onGet;
import static io.github.achirdlabs.rift.dsl.RiftDsl.onPost;
import static io.github.achirdlabs.rift.dsl.RiftDsl.scenario;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslV2ScenarioAndMatchTest {

    @Test
    void scenarioWithoutStartingAtFailsFast() {
        // Building transitions without declaring the start state emits an unsatisfiable guard today;
        // it must fail fast instead.
        ScenarioSpec spec = scenario("checkout")
                .when("empty", onPost("/cart")).respond(created()).goTo("filled");
        IllegalStateException ex = assertThrows(IllegalStateException.class, spec::stubs);
        assertTrue(ex.getMessage().toLowerCase().contains("startingat"),
                "message should tell the caller to call startingAt(...): " + ex.getMessage());
    }

    @Test
    void scenarioWithStartingAtBuilds() {
        List<io.github.achirdlabs.rift.model.Stub> stubs = scenario("checkout")
                .startingAt("empty")
                .when("empty", onPost("/cart")).respond(created()).goTo("filled")
                .when("filled", onGet("/cart")).respond(ok().withTextBody("[]")).goTo("done")
                .stubs();
        assertEquals(2, stubs.size());
        // first transition (from the start state) carries no requiredScenarioState guard
        assertTrue(stubs.get(0).requiredScenarioState().isEmpty());
        assertEquals("filled", stubs.get(0).newScenarioState().orElseThrow());
        // second transition guards on its from-state
        assertEquals("filled", stubs.get(1).requiredScenarioState().orElseThrow());
    }

    @Test
    void scenarioStartingAtDeclaredAfterTransitionStillOmitsStartGuard() {
        // startingAt(...) declared AFTER the transition must still resolve the start-state transition's
        // requiredScenarioState to empty — the guard is computed lazily against the final start state.
        List<io.github.achirdlabs.rift.model.Stub> stubs = scenario("checkout")
                .when("empty", onPost("/cart")).respond(created()).goTo("filled")
                .startingAt("empty")
                .stubs();
        assertEquals(1, stubs.size());
        assertTrue(stubs.get(0).requiredScenarioState().isEmpty(),
                "the transition out of the (later-declared) start state must carry no requiredScenarioState");
        assertEquals("filled", stubs.get(0).newScenarioState().orElseThrow());
    }

    @Test
    void stubSpecImplementsRequestMatch() {
        RequestMatch match = onGet("/api/users/1");
        List<Predicate> predicates = match.predicates();
        assertEquals(1, predicates.size());
        PredicateOperation.Equals op = (PredicateOperation.Equals) predicates.get(0).operation();
        assertEquals("/api/users/1", ((io.github.achirdlabs.rift.json.JsonString) op.fields().get("path")).value());
        assertEquals("GET", ((io.github.achirdlabs.rift.json.JsonString) op.fields().get("method")).value());
    }
}
