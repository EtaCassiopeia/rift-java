package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.dsl.StubSpec;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.Stub;

import java.util.Optional;

/** A thin, transport-backed reference to one stub on a live imposter — no local caching. */
public interface StubRef {

    int index();

    Optional<String> id();

    Stub definition();

    void replace(StubSpec spec);

    /**
     * Raw-JSON form of {@link #replace(StubSpec)} — {@code stub} replaces this reference's target
     * as-is, preserving fields the DSL cannot express ({@code extra}, {@code _rift}); the engine
     * validates the content.
     */
    void replace(JsonValue stub);

    void delete();
}
