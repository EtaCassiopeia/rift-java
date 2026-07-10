package io.github.etacassiopeia.rift.codec;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Internal wiring between the {@link io.github.etacassiopeia.rift.dsl.RiftDsl} / {@code
 * RecordedRequest} call sites and the {@link RiftBodyCodec} SPI. Not part of the supported public
 * API — {@link io.github.etacassiopeia.rift.dsl.RiftDsl#useBodyCodec} is the entry point callers
 * use to register an explicit codec.
 */
public final class BodyCodecs {

    private BodyCodecs() {}

    /** Set by {@code RiftDsl.useBodyCodec}; {@code null} means "no explicit override, fall back to discovery". */
    public static volatile RiftBodyCodec explicit;

    /**
     * Cached result of the (at most once) {@link ServiceLoader} lookup. {@code null} means "not yet
     * computed"; {@link Optional#empty()} means "computed, nothing found" — so the ServiceLoader
     * scan itself only ever runs once, even when it finds nothing.
     */
    private static volatile Optional<RiftBodyCodec> discovered;

    /**
     * Resolves the codec to use: an explicitly-registered one always wins and is checked fresh on
     * every call (so tests that set then reset {@link #explicit} see the change immediately); only
     * the {@link ServiceLoader} classpath scan is cached.
     */
    public static RiftBodyCodec resolve() {
        RiftBodyCodec current = explicit;
        if (current != null) {
            return current;
        }
        return discover().orElseThrow(() -> new IllegalStateException(
                "no RiftBodyCodec on the classpath — add io.github.etacassiopeia:rift-java-jackson"));
    }

    private static Optional<RiftBodyCodec> discover() {
        Optional<RiftBodyCodec> cached = discovered;
        if (cached == null) {
            synchronized (BodyCodecs.class) {
                cached = discovered;
                if (cached == null) {
                    cached = ServiceLoader.load(RiftBodyCodec.class).findFirst();
                    discovered = cached;
                }
            }
        }
        return cached;
    }
}
