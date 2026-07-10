package io.github.etacassiopeia.rift.dsl;

import io.github.etacassiopeia.rift.model.Response;

/**
 * A stub response under construction. Sealed over the five shapes a Mountebank/Rift response can
 * take: a literal ("is") response ({@link IsSpec}), a proxy ({@link ProxySpec}), a raw connection
 * fault ({@link FaultSpec}), an inline-JavaScript response ({@link InjectSpec}), or a {@code _rift}
 * script-only response ({@link ScriptSpec}).
 *
 * <p>Only {@link IsSpec} (and, for its own knobs, {@link ProxySpec}) carries chain methods — the
 * type system enforces that a terminal fault/inject/script response cannot be asked for a header, a
 * body, or a behavior, rather than that being a runtime {@code IllegalStateException}.
 */
public sealed interface ResponseSpec permits IsSpec, ProxySpec, FaultSpec, InjectSpec, ScriptSpec {

    /** Builds the immutable {@link Response} this spec represents. */
    Response build();
}
