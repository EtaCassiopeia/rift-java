package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonBool;
import io.github.etacassiopeia.rift.json.JsonNumber;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Small extraction helpers shared by every {@code *Codec}-shaped method in this package. */
final class JsonSupport {

    private JsonSupport() {}

    static JsonObject requireObject(JsonValue value, String context) {
        if (value instanceof JsonObject obj) {
            return obj;
        }
        throw new WireFormatException(context + ": expected a JSON object, got " + typeName(value));
    }

    static JsonArray requireArray(JsonValue value, String context) {
        if (value instanceof JsonArray arr) {
            return arr;
        }
        throw new WireFormatException(context + ": expected a JSON array, got " + typeName(value));
    }

    static String requireString(JsonObject obj, String key) {
        return requireString(obj.get(key), "'" + key + "'");
    }

    static String requireString(JsonValue v, String context) {
        if (v instanceof JsonString s) {
            return s.value();
        }
        throw new WireFormatException(context + ": expected a string, got " + typeName(v));
    }

    static Optional<String> optString(JsonObject obj, String key) {
        JsonValue v = obj.get(key);
        if (v == null) {
            return Optional.empty();
        }
        if (v instanceof JsonString s) {
            return Optional.of(s.value());
        }
        throw new WireFormatException("'" + key + "': expected a string, got " + typeName(v));
    }

    static boolean optBool(JsonObject obj, String key, boolean defaultValue) {
        JsonValue v = obj.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof JsonBool b) {
            return b.value();
        }
        throw new WireFormatException("'" + key + "': expected a boolean, got " + typeName(v));
    }

    static Optional<Boolean> optBoolBox(JsonObject obj, String key) {
        JsonValue v = obj.get(key);
        if (v == null) {
            return Optional.empty();
        }
        if (v instanceof JsonBool b) {
            return Optional.of(b.value());
        }
        throw new WireFormatException("'" + key + "': expected a boolean, got " + typeName(v));
    }

    static int requireInt(JsonObject obj, String key) {
        return requireNumber(obj.get(key), key).asInt();
    }

    static int optInt(JsonObject obj, String key, int defaultValue) {
        JsonValue v = obj.get(key);
        if (v == null) {
            return defaultValue;
        }
        return requireNumber(v, key).asInt();
    }

    static long optLong(JsonObject obj, String key, long defaultValue) {
        JsonValue v = obj.get(key);
        if (v == null) {
            return defaultValue;
        }
        return requireNumber(v, key).asLong();
    }

    static double optDouble(JsonObject obj, String key, double defaultValue) {
        JsonValue v = obj.get(key);
        if (v == null) {
            return defaultValue;
        }
        return requireNumber(v, key).asDouble();
    }

    static Optional<Integer> optIntBox(JsonObject obj, String key) {
        JsonValue v = obj.get(key);
        if (v == null) {
            return Optional.empty();
        }
        return Optional.of(requireNumber(v, key).asInt());
    }

    static Optional<Long> optLongBox(JsonObject obj, String key) {
        JsonValue v = obj.get(key);
        if (v == null) {
            return Optional.empty();
        }
        return Optional.of(requireNumber(v, key).asLong());
    }

    private static JsonNumber requireNumber(JsonValue v, String key) {
        if (v instanceof JsonNumber n) {
            return n;
        }
        throw new WireFormatException("'" + key + "': expected a number, got " + typeName(v));
    }

    static <T> List<T> optArray(JsonObject obj, String key, Function<JsonValue, T> elementReader) {
        JsonValue v = obj.get(key);
        if (v == null) {
            return List.of();
        }
        List<T> out = new ArrayList<>();
        for (JsonValue el : requireArray(v, key).items()) {
            out.add(elementReader.apply(el));
        }
        return List.copyOf(out);
    }

    /** Order-preserving unmodifiable copy, so re-serialized output stays deterministic. */
    static <K, V> Map<K, V> orderedCopy(Map<K, V> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    static String typeName(JsonValue v) {
        // instanceof chain, not a pattern-matching switch: see JsonWriter for why.
        if (v == null) {
            return "absent";
        } else if (v instanceof JsonObject) {
            return "object";
        } else if (v instanceof JsonArray) {
            return "array";
        } else if (v instanceof JsonString) {
            return "string";
        } else if (v instanceof JsonNumber) {
            return "number";
        } else if (v instanceof JsonBool) {
            return "boolean";
        } else {
            return "null";
        }
    }
}
