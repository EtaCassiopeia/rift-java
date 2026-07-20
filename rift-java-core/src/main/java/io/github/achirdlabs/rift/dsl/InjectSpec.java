package io.github.achirdlabs.rift.dsl;

import io.github.achirdlabs.rift.model.Response;

/**
 * A response entirely computed by inline JavaScript (Mountebank's {@code inject} response),
 * produced by {@link RiftDsl#inject(String)}: a terminal {@link ResponseSpec} variant with no chain
 * methods.
 */
public final class InjectSpec implements ResponseSpec {

    private final String javascript;

    private InjectSpec(String javascript) {
        this.javascript = javascript;
    }

    static InjectSpec of(String javascript) {
        return new InjectSpec(javascript);
    }

    @Override
    public Response build() {
        return new Response.Inject(javascript);
    }
}
