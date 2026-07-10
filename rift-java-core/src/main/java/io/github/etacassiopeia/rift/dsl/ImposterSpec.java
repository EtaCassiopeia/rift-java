package io.github.etacassiopeia.rift.dsl;

import io.github.etacassiopeia.rift.model.ImposterDefinition;
import io.github.etacassiopeia.rift.model.Stub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An imposter under construction, created by {@link RiftDsl#imposter(String)}.
 *
 * <p>Instances are immutable: every chain method returns a new {@code ImposterSpec}. The terminal
 * {@link #build()} produces the {@link ImposterDefinition} model value.
 */
public final class ImposterSpec {

    private final String name;
    private final Optional<Integer> port;
    private final String protocol;
    private final boolean recordRequests;
    private final boolean recordMatches;
    private final boolean allowCors;
    private final List<Stub> stubs;
    private final Optional<ResponseSpec> defaultResponse;

    ImposterSpec(String name) {
        this(name, Optional.empty(), ImposterDefinition.DEFAULT_PROTOCOL, false, false, false, List.of(), Optional.empty());
    }

    private ImposterSpec(
            String name,
            Optional<Integer> port,
            String protocol,
            boolean recordRequests,
            boolean recordMatches,
            boolean allowCors,
            List<Stub> stubs,
            Optional<ResponseSpec> defaultResponse) {
        this.name = name;
        this.port = port;
        this.protocol = protocol;
        this.recordRequests = recordRequests;
        this.recordMatches = recordMatches;
        this.allowCors = allowCors;
        this.stubs = stubs;
        this.defaultResponse = defaultResponse;
    }

    /** Binds the imposter to a fixed port, rather than letting the engine assign one. */
    public ImposterSpec port(int port) {
        return new ImposterSpec(name, Optional.of(port), protocol, recordRequests, recordMatches, allowCors, stubs, defaultResponse);
    }

    /** Sets the imposter's protocol (e.g. {@code "http"}, {@code "https"}, {@code "tcp"}). */
    public ImposterSpec protocol(String protocol) {
        return new ImposterSpec(name, port, protocol, recordRequests, recordMatches, allowCors, stubs, defaultResponse);
    }

    /** Enables recording of every request the imposter receives (sets {@code recordRequests}). */
    public ImposterSpec record() {
        return new ImposterSpec(name, port, protocol, true, recordMatches, allowCors, stubs, defaultResponse);
    }

    /** Enables recording of which stub matched each request (sets {@code recordMatches}). */
    public ImposterSpec recordMatches() {
        return new ImposterSpec(name, port, protocol, recordRequests, true, allowCors, stubs, defaultResponse);
    }

    /** Enables permissive CORS response headers for this imposter (sets {@code allowCORS}). */
    public ImposterSpec allowCors() {
        return new ImposterSpec(name, port, protocol, recordRequests, recordMatches, true, stubs, defaultResponse);
    }

    /** Sets the response served when no stub matches a request. */
    public ImposterSpec defaultResponse(ResponseSpec response) {
        return new ImposterSpec(name, port, protocol, recordRequests, recordMatches, allowCors, stubs, Optional.of(response));
    }

    /** Appends one or more stubs, in the given order. */
    public ImposterSpec stub(StubSpec... specs) {
        List<Stub> next = new ArrayList<>(stubs);
        for (StubSpec spec : specs) {
            next.add(spec.build());
        }
        return new ImposterSpec(name, port, protocol, recordRequests, recordMatches, allowCors, List.copyOf(next), defaultResponse);
    }

    /** Appends already-built stubs, in the given order — e.g. the output of {@link ScenarioSpec#stubs()}. */
    public ImposterSpec stub(List<Stub> builtStubs) {
        List<Stub> next = new ArrayList<>(stubs);
        next.addAll(builtStubs);
        return new ImposterSpec(name, port, protocol, recordRequests, recordMatches, allowCors, List.copyOf(next), defaultResponse);
    }

    /** Builds the immutable {@link ImposterDefinition} this spec represents. */
    public ImposterDefinition build() {
        return new ImposterDefinition(
                port, Optional.empty(), protocol, Optional.empty(), Optional.empty(), Optional.of(name),
                recordRequests, recordMatches, stubs, defaultResponse.map(ResponseSpec::buildIsResponse),
                Optional.empty(), allowCors, false, Optional.empty(), Optional.empty(), Optional.empty(), Map.of());
    }
}
