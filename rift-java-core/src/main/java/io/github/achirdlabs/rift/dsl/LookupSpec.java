package io.github.achirdlabs.rift.dsl;

import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonString;
import io.github.achirdlabs.rift.json.JsonValue;

import java.util.Optional;

/**
 * A single {@code lookup} behavior entry under construction, produced by {@link
 * RiftDsl#lookupKey(String)}: resolves a key extracted from the request against a data source (a
 * CSV file, currently the only source) and injects the matching row into the response.
 *
 * <p>There is no typed {@code model.Lookup} — {@link IsSpec#lookup(LookupSpec...)} embeds this
 * spec's built {@link #build()} JSON directly into a {@code Behavior.Unknown} array, so it
 * round-trips losslessly while still emitting the correct wire shape.
 *
 * <p>Instances are immutable: every chain method returns a new {@code LookupSpec}.
 */
public final class LookupSpec {

    private final String keyFrom;
    private final Optional<ExtractionSpec> using;
    private final Optional<String> csvPath;
    private final Optional<String> csvKeyColumn;
    private final Optional<String> into;

    private LookupSpec(
            String keyFrom,
            Optional<ExtractionSpec> using,
            Optional<String> csvPath,
            Optional<String> csvKeyColumn,
            Optional<String> into) {
        this.keyFrom = keyFrom;
        this.using = using;
        this.csvPath = csvPath;
        this.csvKeyColumn = csvKeyColumn;
        this.into = into;
    }

    static LookupSpec key(String from) {
        return new LookupSpec(from, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** Sets the extraction method (regex/jsonpath/xpath) applied to the key field. */
    public LookupSpec using(ExtractionSpec extraction) {
        return new LookupSpec(keyFrom, Optional.of(extraction), csvPath, csvKeyColumn, into);
    }

    /** Resolves the key against the given CSV file, matching rows on {@code keyColumn}. */
    public LookupSpec fromCsv(String path, String keyColumn) {
        return new LookupSpec(keyFrom, using, Optional.of(path), Optional.of(keyColumn), into);
    }

    /** Names the {@code ${token}} placeholder the matched row is injected under. */
    public LookupSpec into(String token) {
        return new LookupSpec(keyFrom, using, csvPath, csvKeyColumn, Optional.of(token));
    }

    /** Builds this lookup entry's raw wire JSON: {@code {"key":..., "fromDataSource":..., "into":...}}. */
    JsonValue build() {
        ExtractionSpec extraction = using.orElseThrow(() -> new IllegalStateException("lookupKey(\"" + keyFrom + "\") requires .using(...)"));
        String path = csvPath.orElseThrow(() -> new IllegalStateException("lookupKey(\"" + keyFrom + "\") requires .fromCsv(...)"));
        String keyColumn = csvKeyColumn.orElseThrow();
        String token = into.orElseThrow(() -> new IllegalStateException("lookupKey(\"" + keyFrom + "\") requires .into(...)"));

        JsonObject key = JsonObject.builder()
                .put("from", new JsonString(keyFrom))
                .put("using", extraction.toJsonValue())
                .build();
        JsonObject csv = JsonObject.builder()
                .put("path", new JsonString(path))
                .put("keyColumn", new JsonString(keyColumn))
                .build();
        JsonObject fromDataSource = JsonObject.builder().put("csv", csv).build();

        return JsonObject.builder()
                .put("key", key)
                .put("fromDataSource", fromDataSource)
                .put("into", new JsonString(token))
                .build();
    }
}
