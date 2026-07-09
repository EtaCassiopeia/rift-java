package io.github.etacassiopeia.rift.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hard requirement (issue #2): ports are respected verbatim. An explicit port survives unchanged;
 * an absent port stays absent and is never invented on the way back out — this designs out the
 * imposterFromRaw port-clobber bug class the issue named.
 */
class PortHandlingTest {

    @Test
    void explicitPortSurvivesVerbatim() {
        Imposter imposter = Imposter.fromJson("""
                {"port": 4545, "protocol": "http", "stubs": []}
                """);

        assertEquals(Optional.of(4545), imposter.port());
        assertTrue(imposter.toJson().contains("\"port\":4545"), imposter.toJson());
    }

    @Test
    void omittedPortStaysAbsentAndIsNeverInvented() {
        Imposter imposter = Imposter.fromJson("""
                {"protocol": "http", "stubs": []}
                """);

        assertEquals(Optional.empty(), imposter.port());
        assertFalse(imposter.toJson().contains("\"port\""), imposter.toJson());
    }

    @Test
    void omittedPortRoundTripsThroughImposters() {
        String json = """
                {"imposters": [{"protocol": "http", "stubs": []}]}
                """;
        Imposters model = Imposters.fromJson(json);
        assertEquals(Optional.empty(), model.imposters().get(0).port());

        Imposters roundTripped = Imposters.fromJson(model.toJson());
        assertEquals(Optional.empty(), roundTripped.imposters().get(0).port());
        assertFalse(model.toJson().contains("\"port\""), model.toJson());
    }
}
