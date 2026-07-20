package io.github.achirdlabs.rift.conformance;

import io.github.achirdlabs.rift.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate for the embedded transport's fixture rewrite (AC2). The in-process engine inherits the JVM
 * working directory, so a fixture's {@code fromDataSource.csv.path} relative to {@code corpus/} would
 * not resolve; {@link Corpus#rewriteForEmbedded} absolutizes it under the corpus payload root.
 */
class CorpusRewriteTest {

    private static final Path PAYLOAD = Path.of("/corpus/root").toAbsolutePath();

    @Test
    void absolutizesRelativeCsvDataSourcePaths() {
        JsonValue imposter = JsonValue.parse("""
                {"protocol":"http","port":4500,"stubs":[{
                  "responses":[{"is":{"statusCode":200},
                    "_behaviors":{"lookup":{
                      "key":{"from":"path","using":{"method":"regex","selector":"/c/(\\\\d+)"}},
                      "fromDataSource":{"csv":{"path":"data/products.csv","keyColumn":"id"}},
                      "into":"${row}"}}}]}]}
                """);

        JsonValue rewritten = Corpus.rewriteForEmbedded(imposter, PAYLOAD);

        String expected = PAYLOAD.resolve("data/products.csv").toString();
        assertTrue(rewritten.toJson().contains(expected.replace("\\", "\\\\")),
                () -> "csv path must be absolutized under the payload root; got: " + rewritten.toJson());
        assertFalse(rewritten.toJson().contains("\"data/products.csv\""),
                "the relative csv path must not survive the rewrite");
    }

    @Test
    void leavesAbsolutePathsAndUnrelatedFieldsUntouched() {
        String absolute = PAYLOAD.resolve("data/x.csv").toString().replace("\\", "\\\\");
        JsonValue imposter = JsonValue.parse("""
                {"protocol":"http","port":4500,"name":"data/decoy-not-a-path","stubs":[{
                  "responses":[{"is":{"statusCode":200,"body":"data/not-a-source"},
                    "_behaviors":{"lookup":{"fromDataSource":{"csv":{"path":"%s","keyColumn":"id"}},"into":"${r}"}}}]}]}
                """.formatted(absolute));

        JsonValue rewritten = Corpus.rewriteForEmbedded(imposter, PAYLOAD);

        assertTrue(JsonValue.semanticEquals(imposter, rewritten),
                () -> "an already-absolute csv path and lookalike strings elsewhere must be untouched; got: "
                        + rewritten.toJson());
    }

    @Test
    void isIdentityWhenNoDataSourcePresent() {
        JsonValue imposter = JsonValue.parse(
                "{\"protocol\":\"http\",\"port\":4500,\"stubs\":[{\"responses\":[{\"is\":{\"statusCode\":200,\"body\":\"data/x\"}}]}]}");
        assertEquals(imposter.toJson(), Corpus.rewriteForEmbedded(imposter, PAYLOAD).toJson());
    }

    @Test
    void absolutizesEveryCsvSourceAcrossMultipleStubs() {
        JsonValue imposter = JsonValue.parse("""
                {"protocol":"http","port":4500,"stubs":[
                  {"responses":[{"is":{"statusCode":200},
                    "_behaviors":{"lookup":{"fromDataSource":{"csv":{"path":"data/products.csv","keyColumn":"id"}},"into":"${a}"}}}]},
                  {"responses":[{"is":{"statusCode":200},
                    "_behaviors":{"lookup":{"fromDataSource":{"csv":{"path":"data/users.csv","keyColumn":"id"}},"into":"${b}"}}}]}]}
                """);

        String rewritten = Corpus.rewriteForEmbedded(imposter, PAYLOAD).toJson();

        assertTrue(rewritten.contains(PAYLOAD.resolve("data/products.csv").toString().replace("\\", "\\\\")),
                () -> "first stub's csv path must be absolutized; got: " + rewritten);
        assertTrue(rewritten.contains(PAYLOAD.resolve("data/users.csv").toString().replace("\\", "\\\\")),
                () -> "second stub's csv path must be absolutized; got: " + rewritten);
        assertFalse(rewritten.contains("\"data/products.csv\"") || rewritten.contains("\"data/users.csv\""),
                "no relative csv path may survive");
    }

    @Test
    void leavesANonCsvDataSourceUntouched() {
        // A future/non-csv data source (no `csv` object) has no path this rewrite understands, so the
        // document must pass through unchanged rather than being corrupted.
        JsonValue imposter = JsonValue.parse(
                "{\"protocol\":\"http\",\"port\":4500,\"stubs\":[{\"responses\":[{\"is\":{\"statusCode\":200},"
                        + "\"_behaviors\":{\"lookup\":{\"fromDataSource\":{\"json\":{\"path\":\"data/x.json\"}},\"into\":\"${r}\"}}}]}]}");

        assertTrue(JsonValue.semanticEquals(imposter, Corpus.rewriteForEmbedded(imposter, PAYLOAD)),
                "a data source without a csv object must be left untouched");
    }
}
