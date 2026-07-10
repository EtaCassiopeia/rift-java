package io.github.etacassiopeia.rift.dsl;

import io.github.etacassiopeia.rift.model.Response;

/**
 * A raw connection-fault response, produced by {@link RiftDsl#fault(Fault)}: a terminal {@link
 * ResponseSpec} variant that carries no headers/body/behaviors of its own, so it has no chain
 * methods at all.
 */
public final class FaultSpec implements ResponseSpec {

    private final String faultName;

    private FaultSpec(String faultName) {
        this.faultName = faultName;
    }

    static FaultSpec of(Fault fault) {
        return new FaultSpec(fault.name());
    }

    @Override
    public Response build() {
        return new Response.Fault(faultName);
    }
}
