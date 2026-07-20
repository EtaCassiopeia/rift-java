package io.github.achirdlabs.rift.conformance;

import io.github.achirdlabs.rift.json.JsonArray;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON normalizations shared by the corpus gates. */
final class Normalize {

    private Normalize() {}

    /**
     * Drops the {@code _verify} annotation from every stub. {@code _verify} carries the fixture's
     * expected request/response transcripts — a test artifact, not served configuration — so the
     * typed DSL never emits it. The DSL-expressibility gate compares the served config, so both
     * sides are stripped of {@code _verify} before comparison.
     */
    static JsonValue stripVerify(JsonValue imposter) {
        if (!(imposter instanceof JsonObject obj) || !(obj.get("stubs") instanceof JsonArray stubs)) {
            return imposter;
        }
        List<JsonValue> cleanedStubs = stubs.items().stream().map(Normalize::stripStubVerify).toList();
        Map<String, JsonValue> fields = new LinkedHashMap<>(obj.fields());
        fields.put("stubs", JsonArray.of(cleanedStubs));
        return new JsonObject(fields);
    }

    private static JsonValue stripStubVerify(JsonValue stub) {
        if (!(stub instanceof JsonObject obj) || !obj.has("_verify")) {
            return stub;
        }
        Map<String, JsonValue> fields = new LinkedHashMap<>(obj.fields());
        fields.remove("_verify");
        return new JsonObject(fields);
    }
}
