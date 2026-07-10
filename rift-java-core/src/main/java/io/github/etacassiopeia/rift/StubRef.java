package io.github.etacassiopeia.rift;

import io.github.etacassiopeia.rift.dsl.StubSpec;
import io.github.etacassiopeia.rift.model.Stub;

import java.util.Optional;

/** A thin, transport-backed reference to one stub on a live imposter — no local caching. */
public interface StubRef {

    int index();

    Optional<String> id();

    Stub definition();

    void replace(StubSpec spec);

    void delete();
}
