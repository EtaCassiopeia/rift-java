package io.github.etacassiopeia.rift.verify;

import io.github.etacassiopeia.rift.model.Predicate;

import java.util.List;

/**
 * Something that matches requests against a list of predicates — implemented by {@code StubSpec} so
 * a stub under construction can be inspected/verified before (or without) being built into a {@code
 * Stub}.
 */
public interface RequestMatch {

    /** The predicates a request must satisfy to match. */
    List<Predicate> predicates();
}
