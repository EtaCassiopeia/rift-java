package io.github.achirdlabs.rift.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.achirdlabs.rift.codec.RiftBodyCodec;
import io.github.achirdlabs.rift.json.JsonArray;
import io.github.achirdlabs.rift.json.JsonBool;
import io.github.achirdlabs.rift.json.JsonNull;
import io.github.achirdlabs.rift.json.JsonNumber;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonString;
import io.github.achirdlabs.rift.json.JsonValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@link RiftBodyCodec} SPI implementation backed by Jackson's {@link ObjectMapper}. Registered
 * for {@link java.util.ServiceLoader} discovery via {@code META-INF/services}, so adding this
 * artifact as a dependency is enough for {@code RiftDsl}'s {@code Object}-typed body overloads to
 * work without any explicit wiring.
 */
public final class JacksonBodyCodec implements RiftBodyCodec {

    private final ObjectMapper mapper;

    /** A codec backed by a fresh {@link ObjectMapper} with all discoverable modules (incl. JSR-310) registered. */
    public JacksonBodyCodec() {
        this(new ObjectMapper().findAndRegisterModules());
    }

    /** A codec backed by a caller-supplied, already-configured {@link ObjectMapper}. */
    public JacksonBodyCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public JsonValue toJson(Object value) {
        return toJsonValue(mapper.valueToTree(value));
    }

    @Override
    public <T> T fromJson(JsonValue json, Class<T> type) {
        try {
            // Compact-string hop through the wire model: simple, and fine for the codec's use case
            // (test fixtures and recorded-request bodies), rather than a second Jackson tree-walk.
            return mapper.readValue(json.toJson(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to decode JSON body as " + type.getName(), e);
        }
    }

    private static JsonValue toJsonValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return JsonNull.INSTANCE;
        }
        if (node.isObject()) {
            Map<String, JsonValue> fields = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), toJsonValue(entry.getValue())));
            return new JsonObject(fields);
        }
        if (node.isArray()) {
            List<JsonValue> items = new ArrayList<>();
            node.forEach(item -> items.add(toJsonValue(item)));
            return JsonArray.of(items);
        }
        if (node.isBoolean()) {
            return JsonBool.of(node.booleanValue());
        }
        if (node.isNumber()) {
            if ((node.isDouble() || node.isFloat()) && !Double.isFinite(node.doubleValue())) {
                throw new IllegalArgumentException("JSON cannot represent the non-finite number " + node.asText());
            }
            // Raw source text, not asDouble(): a long like 9007199254740993 isn't representable as a
            // double, so round-tripping through a float would silently corrupt it.
            return new JsonNumber(node.asText());
        }
        if (node.isTextual()) {
            return new JsonString(node.textValue());
        }
        if (node.isBinary()) {
            return new JsonString(node.asText()); // Jackson encodes a byte[] as a base64 string
        }
        // POJONode / any other node has no faithful JSON form — fail loudly rather than emit its toString().
        throw new IllegalArgumentException("cannot encode Jackson node of type " + node.getNodeType() + " as JSON");
    }
}
