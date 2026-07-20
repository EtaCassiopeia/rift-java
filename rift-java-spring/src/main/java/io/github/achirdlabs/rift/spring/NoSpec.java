package io.github.achirdlabs.rift.spring;

import io.github.achirdlabs.rift.dsl.ImposterSpec;

import java.util.function.Supplier;

/**
 * Sentinel default for {@link ConfigureImposter#spec()} meaning "a bare recording imposter". The
 * integration detects this class by identity and never invokes {@link #get()}.
 */
public final class NoSpec implements Supplier<ImposterSpec> {

    @Override
    public ImposterSpec get() {
        throw new UnsupportedOperationException("NoSpec is a sentinel and must not be instantiated for its spec");
    }
}
