package io.github.achirdlabs.rift.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the wire-model record names to {@code ImposterDefinition} / {@code ImposterDefinitions}
 * (freeing the {@code Imposter} name for the transport-bound handle introduced by #4). A pure
 * naming gate: it references the types by their new names so an incomplete rename fails to compile.
 */
class RenameTest {

    @Test
    void imposterDefinitionParsesAndRoundTrips() {
        ImposterDefinition def = ImposterDefinition.fromJson("""
                {"port": 4545, "protocol": "http", "stubs": []}
                """);
        assertEquals(4545, def.port().orElseThrow());
        assertEquals("http", def.protocol());
        assertEquals(def, ImposterDefinition.fromJson(def.toJson()));
    }

    @Test
    void imposterDefinitionsCollectionParsesAndRoundTrips() {
        ImposterDefinitions docs = ImposterDefinitions.fromJson("""
                {"imposters": [{"port": 4545, "protocol": "http", "stubs": []}]}
                """);
        assertEquals(1, docs.imposters().size());
        assertTrue(docs.toJson().contains("\"imposters\""));
        assertEquals(docs, ImposterDefinitions.fromJson(docs.toJson()));
    }
}
