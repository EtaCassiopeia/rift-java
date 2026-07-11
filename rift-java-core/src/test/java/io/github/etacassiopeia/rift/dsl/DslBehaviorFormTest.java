package io.github.etacassiopeia.rift.dsl;

import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.model.Behavior;
import io.github.etacassiopeia.rift.model.CopyEntry;
import io.github.etacassiopeia.rift.model.Response;
import io.github.etacassiopeia.rift.model.WaitSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.etacassiopeia.rift.dsl.RiftDsl.copyFrom;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.copyFromHeader;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.copyFromQuery;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.lookupKey;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.regex;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fast, corpus-independent coverage of the object-form DSL builders added for issue #55
 * ({@code copyObject}, {@code lookupObject}, {@code waitScript}, {@code copyFromQuery/Header}) — the
 * conformance corpus exercises them end-to-end, but this gives {@code mvn test} in core direct signal.
 */
class DslBehaviorFormTest {

    private static List<Behavior> behaviorsOf(IsSpec spec) {
        return ((Response.Is) spec.build()).behaviors().entries();
    }

    @Test
    void copyObjectEmitsTheObjectWireForm() {
        Behavior.Copy copy = (Behavior.Copy) behaviorsOf(
                status(200).copyObject(copyFrom("path").into("${id}").using(regex("/o/(\\d+)")))).get(0);
        assertTrue(copy.objectForm());
        assertTrue(copy.value() instanceof JsonObject, "object-form copy serializes to an object");
    }

    @Test
    void copyFromQueryAndHeaderProduceObjectFromValues() {
        CopyEntry fromQuery = ((Behavior.Copy) behaviorsOf(
                status(200).copyObject(copyFromQuery("user").into("${u}").using(regex("(.*)")))).get(0)).entries().get(0);
        assertEquals("{\"query\":\"user\"}", fromQuery.from().toJson());

        CopyEntry fromHeader = ((Behavior.Copy) behaviorsOf(
                status(200).copyObject(copyFromHeader("X-Request-Id").into("${r}").using(regex("(.*)")))).get(0)).entries().get(0);
        assertEquals("{\"headers\":\"X-Request-Id\"}", fromHeader.from().toJson());
    }

    @Test
    void copyArrayFormStaysAnArray() {
        Behavior.Copy copy = (Behavior.Copy) behaviorsOf(status(200).copy(
                copyFrom("path").into("${a}").using(regex("a")),
                copyFrom("body").into("${b}").using(regex("b")))).get(0);
        assertTrue(copy.value() instanceof JsonArray array && array.items().size() == 2,
                "multi-entry copy serializes to a 2-element array");
    }

    @Test
    void lookupObjectRidesUnknownAsAnObject() {
        Behavior.Unknown lookup = (Behavior.Unknown) behaviorsOf(status(200).lookupObject(
                lookupKey("path").using(regex("/c/(\\d+)")).fromCsv("data/products.csv", "id").into("${row}"))).get(0);
        assertEquals("lookup", lookup.key());
        assertTrue(lookup.raw() instanceof JsonObject, "object-form lookup serializes to an object");
    }

    @Test
    void waitScriptEmitsTheBareStringForm() {
        Behavior.Wait wait = (Behavior.Wait) behaviorsOf(status(200).waitScript("function(){ return 1; }")).get(0);
        assertTrue(wait.spec() instanceof WaitSpec.Script, "waitScript builds the bare-string WaitSpec.Script");
    }

    @Test
    void objectFormCopyRejectsMoreThanOneEntry() {
        Behavior.Copy two = (Behavior.Copy) behaviorsOf(status(200).copy(
                copyFrom("path").into("${a}").using(regex("a")),
                copyFrom("body").into("${b}").using(regex("b")))).get(0);
        assertThrows(IllegalArgumentException.class, () -> new Behavior.Copy(two.entries(), true),
                "object-form copy must have exactly one entry");
    }
}
