package io.github.achirdlabs.rift.dsl;

import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonString;
import io.github.achirdlabs.rift.json.JsonValue;
import io.github.achirdlabs.rift.model.CopyEntry;

import java.util.Optional;

/**
 * A single {@code copy} behavior entry under construction, produced by {@link RiftDsl#copyFrom(String)}
 * (or {@link RiftDsl#copyFromQuery(String)} / {@link RiftDsl#copyFromHeader(String)} for the object
 * {@code from} form): extracts a value from the request and injects it into the response as a
 * {@code ${token}} placeholder.
 *
 * <p>Instances are immutable: every chain method returns a new {@code CopySpec}. The terminal
 * {@link #build()} (invoked by {@link IsSpec#copy(CopySpec...)}) produces the {@link CopyEntry}
 * model value.
 */
public final class CopySpec {

    private final JsonValue from;
    private final Optional<String> into;
    private final Optional<ExtractionSpec> using;

    private CopySpec(JsonValue from, Optional<String> into, Optional<ExtractionSpec> using) {
        this.from = from;
        this.into = into;
        this.using = using;
    }

    /** Copies from a named request part ({@code "path"}, {@code "body"}, …) — the bare-string {@code from}. */
    static CopySpec from(String from) {
        return new CopySpec(new JsonString(from), Optional.empty(), Optional.empty());
    }

    /** Copies from a named query parameter — the object {@code from} form {@code {"query": name}}. */
    public static CopySpec fromQuery(String name) {
        return new CopySpec(JsonObject.builder().put("query", new JsonString(name)).build(), Optional.empty(), Optional.empty());
    }

    /** Copies from a named request header — the object {@code from} form {@code {"headers": name}}. */
    public static CopySpec fromHeader(String name) {
        return new CopySpec(JsonObject.builder().put("headers", new JsonString(name)).build(), Optional.empty(), Optional.empty());
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
        String token = into.orElseThrow(() -> new IllegalStateException("copy from " + from.toJson() + " requires .into(...)"));
        ExtractionSpec extraction = using.orElseThrow(() -> new IllegalStateException("copy from " + from.toJson() + " requires .using(...)"));
        return new CopyEntry(from, token, extraction.build());
    }
}
