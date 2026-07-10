package io.github.etacassiopeia.rift.transport;

/** Addresses a single stub within an imposter's stub list, either by position or by stable id. */
public sealed interface StubAddress permits StubAddress.ByIndex, StubAddress.ById {

    record ByIndex(int index) implements StubAddress {}

    record ById(String id) implements StubAddress {}
}
