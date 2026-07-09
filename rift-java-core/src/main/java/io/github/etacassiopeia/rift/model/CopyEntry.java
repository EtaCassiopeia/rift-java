package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;

/**
 * One {@code copy} behavior entry: extracts a value from the request ({@code from}, via {@code
 * using}) and injects it into the response ({@code into}, a {@code ${token}} placeholder).
 */
public record CopyEntry(JsonValue from, String into, Using using) {

    public CopyEntry {
        java.util.Objects.requireNonNull(from, "from");
        java.util.Objects.requireNonNull(into, "into");
        java.util.Objects.requireNonNull(using, "using");
    }

    static CopyEntry read(JsonObject obj) {
        JsonValue from = obj.get("from");
        if (from == null) {
            throw new WireFormatException("'copy' entry missing 'from'");
        }
        return new CopyEntry(from, JsonSupport.requireString(obj, "into"), Using.read(JsonSupport.requireObject(obj.get("using"), "using")));
    }

    JsonObject toJsonValue() {
        return JsonObject.builder().put("from", from).put("into", new JsonString(into)).put("using", using.toJsonValue()).build();
    }
}
