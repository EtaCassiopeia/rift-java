package io.github.achirdlabs.rift.model;

import io.github.achirdlabs.rift.json.JsonNumber;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonString;
import io.github.achirdlabs.rift.json.JsonValue;

/** The value of a {@code wait} behavior: a fixed delay, a random range, or a script that computes it. */
public sealed interface WaitSpec {

    record Fixed(long ms) implements WaitSpec {}

    record Range(long minMs, long maxMs) implements WaitSpec {}

    record Inject(String script) implements WaitSpec {}

    /** A {@code wait} given as a bare string (a function body / named latency); round-trips verbatim. */
    record Script(String source) implements WaitSpec {}

    static WaitSpec read(JsonValue value) {
        if (value instanceof JsonNumber n) {
            return new Fixed(n.asLong());
        }
        if (value instanceof JsonString s) {
            return new Script(s.value());
        }
        if (value instanceof JsonObject obj) {
            if (obj.has("inject")) {
                return new Inject(JsonSupport.requireString(obj, "inject"));
            }
            if (obj.has("min") && obj.has("max")) {
                return new Range(JsonSupport.optLong(obj, "min", 0), JsonSupport.optLong(obj, "max", 0));
            }
            throw new WireFormatException("'wait': object must have 'inject' or 'min'/'max'");
        }
        throw new WireFormatException("'wait': expected a number, string, or object, got " + JsonSupport.typeName(value));
    }

    default JsonValue toJsonValue() {
        if (this instanceof Fixed fixed) {
            return JsonNumber.of(fixed.ms());
        }
        if (this instanceof Range range) {
            return JsonObject.builder().put("min", JsonNumber.of(range.minMs())).put("max", JsonNumber.of(range.maxMs())).build();
        }
        if (this instanceof Inject inject) {
            return JsonObject.builder().put("inject", new JsonString(inject.script())).build();
        }
        if (this instanceof Script script) {
            return new JsonString(script.source());
        }
        throw new IllegalStateException("unreachable: " + this);
    }
}
