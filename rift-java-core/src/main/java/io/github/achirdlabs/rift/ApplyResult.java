package io.github.achirdlabs.rift;

import io.github.achirdlabs.rift.error.CommunicationError;
import io.github.achirdlabs.rift.json.JsonArray;
import io.github.achirdlabs.rift.json.JsonNumber;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

/** The outcome of {@link Rift#applyConfig(JsonValue)}: how many imposters/stubs changed, and which failed. */
public record ApplyResult(int created, int replaced, int stubPatched, int deleted, List<JsonValue> failed) {

    public ApplyResult {
        failed = List.copyOf(failed);
    }

    /**
     * Reads an {@code ApplyResult}. Individual missing counters default to 0, but a body that is not a
     * JSON object at all is a {@link CommunicationError} — an all-zeros "nothing happened" result must
     * not be indistinguishable from a garbage response.
     */
    public static ApplyResult read(JsonValue value) {
        if (!(value instanceof JsonObject obj)) {
            throw new CommunicationError("rift admin API /admin/reload response was not a JSON object");
        }
        int created = intOrZero(obj, "created");
        int replaced = intOrZero(obj, "replaced");
        int stubPatched = intOrZero(obj, "stubPatched");
        int deleted = intOrZero(obj, "deleted");
        List<JsonValue> failed = new ArrayList<>();
        if (obj.get("failed") instanceof JsonArray arr) {
            failed.addAll(arr.items());
        }
        return new ApplyResult(created, replaced, stubPatched, deleted, failed);
    }

    private static int intOrZero(JsonObject obj, String key) {
        return obj.get(key) instanceof JsonNumber n ? n.asInt() : 0;
    }
}
