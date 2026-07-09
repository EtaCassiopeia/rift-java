package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonNumber;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;

import java.util.List;

/**
 * One entry of a response's {@code _behaviors} object. Known behavior names get a typed shape;
 * anything else round-trips losslessly through {@link Unknown} so an unrecognized (or
 * future/engine-specific) behavior is never dropped.
 */
public sealed interface Behavior {

    String key();

    record Wait(WaitSpec spec) implements Behavior {
        public String key() { return "wait"; }
    }

    record Decorate(String script) implements Behavior {
        public String key() { return "decorate"; }
    }

    record Copy(List<CopyEntry> entries) implements Behavior {
        public Copy { entries = List.copyOf(entries); }
        public String key() { return "copy"; }
    }

    record Repeat(int count) implements Behavior {
        public String key() { return "repeat"; }
    }

    record ShellTransform(String command) implements Behavior {
        public String key() { return "shellTransform"; }
    }

    record Unknown(String key, JsonValue raw) implements Behavior {}

    static Behavior read(String key, JsonValue value) {
        return switch (key) {
            case "wait" -> new Wait(WaitSpec.read(value));
            case "decorate" -> new Decorate(JsonSupport.requireString(value, "'decorate'"));
            case "copy" -> new Copy(readCopyEntries(value));
            case "repeat" -> new Repeat(numberOf(value, "repeat").asInt());
            case "shellTransform" -> new ShellTransform(JsonSupport.requireString(value, "'shellTransform'"));
            default -> new Unknown(key, value);
        };
    }

    private static List<CopyEntry> readCopyEntries(JsonValue value) {
        return JsonSupport.requireArray(value, "'copy'").items().stream()
                .map(el -> CopyEntry.read(JsonSupport.requireObject(el, "'copy[]'")))
                .toList();
    }

    private static JsonNumber numberOf(JsonValue value, String context) {
        if (value instanceof JsonNumber n) {
            return n;
        }
        throw new WireFormatException("'" + context + "': expected a number, got " + JsonSupport.typeName(value));
    }

    /**
     * This behavior's wire value. Uses an {@code instanceof} chain rather than a pattern-matching
     * {@code switch}: the latter is still a preview feature at the Java 17 release level this
     * module compiles against (finalized only in Java 21).
     */
    default JsonValue value() {
        if (this instanceof Wait wait) {
            return wait.spec().toJsonValue();
        }
        if (this instanceof Decorate decorate) {
            return new JsonString(decorate.script());
        }
        if (this instanceof Copy copy) {
            return new JsonArray(copy.entries().stream().map(CopyEntry::toJsonValue).map(v -> (JsonValue) v).toList());
        }
        if (this instanceof Repeat repeat) {
            return JsonNumber.of(repeat.count());
        }
        if (this instanceof ShellTransform shellTransform) {
            return new JsonString(shellTransform.command());
        }
        if (this instanceof Unknown unknown) {
            return unknown.raw();
        }
        throw new IllegalStateException("unreachable: " + this);
    }
}
