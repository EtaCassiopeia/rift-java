package io.github.etacassiopeia.rift.verify;

import io.github.etacassiopeia.rift.dsl.RiftDsl;
import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.Predicate;
import io.github.etacassiopeia.rift.model.WireFormatException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code RequestMatch.of}/{@code ofJson} verification-path escape hatch (issue #89): a match built
 * from raw predicate JSON is equivalent to the DSL-built one, and malformed input is rejected with a
 * {@link WireFormatException} naming the offending index.
 */
class RequestMatchTest {

    private static String predicatesJson(RequestMatch match) {
        return new JsonArray(match.predicates().stream()
                .map(p -> (JsonValue) JsonValue.parse(p.toJson())).toList()).toJson();
    }

    @Test
    void ofJsonEqualsTheDslBuiltMatch() {
        RequestMatch dsl = RiftDsl.onPost("/api/users").withHeader("Content-Type", RiftDsl.contains("json"));
        RequestMatch fromJson = RequestMatch.ofJson(predicatesJson(dsl));
        assertEquals(dsl.predicates(), fromJson.predicates(),
                "a match rebuilt from the wire predicates array equals the DSL-built one");
    }

    @Test
    void ofVarargsCarriesTheGivenPredicates() {
        List<Predicate> preds = RiftDsl.onGet("/x").predicates();
        assertEquals(preds, RequestMatch.of(preds.toArray(new Predicate[0])).predicates());
    }

    @Test
    void ofJsonRejectsANonArray() {
        WireFormatException e = assertThrows(WireFormatException.class,
                () -> RequestMatch.ofJson("{\"not\":\"an array\"}"));
        assertTrue(e.getMessage().contains("array"), e.getMessage());
    }

    @Test
    void ofJsonNamesTheOffendingPredicateIndex() {
        String bad = "[{\"equals\":{\"path\":\"/x\"}}, \"not-a-predicate\"]";
        WireFormatException e = assertThrows(WireFormatException.class, () -> RequestMatch.ofJson(bad));
        assertTrue(e.getMessage().contains("index 1"), e.getMessage());
    }
}
