package io.github.etacassiopeia.rift.dsl;

import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.model.CopyEntry;

import java.util.Optional;

/**
 * A single {@code copy} behavior entry under construction, produced by {@link
 * RiftDsl#copyFrom(String)}: extracts a value from the request and injects it into the response as
 * a {@code ${token}} placeholder.
 *
 * <p>Instances are immutable: every chain method returns a new {@code CopySpec}. The terminal
 * {@link #build()} (invoked by {@link IsSpec#copy(CopySpec...)}) produces the {@link CopyEntry}
 * model value.
 */
public final class CopySpec {

    private final String from;
    private final Optional<String> into;
    private final Optional<ExtractionSpec> using;

    private CopySpec(String from, Optional<String> into, Optional<ExtractionSpec> using) {
        this.from = from;
        this.into = into;
        this.using = using;
    }

    static CopySpec from(String from) {
        return new CopySpec(from, Optional.empty(), Optional.empty());
    }

    /** Names the {@code ${token}} placeholder the extracted value is injected under. */
    public CopySpec into(String token) {
        return new CopySpec(from, Optional.of(token), using);
    }

    /** Sets the extraction method (regex/jsonpath/xpath) applied to {@code from}. */
    public CopySpec using(ExtractionSpec extraction) {
        return new CopySpec(from, into, Optional.of(extraction));
    }

    /** Builds the immutable {@link CopyEntry} this spec represents. */
    CopyEntry build() {
        String token = into.orElseThrow(() -> new IllegalStateException("copyFrom(\"" + from + "\") requires .into(...)"));
        ExtractionSpec extraction = using.orElseThrow(() -> new IllegalStateException("copyFrom(\"" + from + "\") requires .using(...)"));
        return new CopyEntry(new JsonString(from), token, extraction.build());
    }
}
