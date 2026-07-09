package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The matching operation a predicate performs. Exactly one of these tags appears per predicate
 * JSON object, alongside the flattened {@link PredicateParameters} fields.
 */
public sealed interface PredicateOperation {

    record Equals(Map<String, JsonValue> fields) implements PredicateOperation {
        public Equals { fields = JsonSupport.orderedCopy(fields); }
    }

    record DeepEquals(Map<String, JsonValue> fields) implements PredicateOperation {
        public DeepEquals { fields = JsonSupport.orderedCopy(fields); }
    }

    record Contains(Map<String, JsonValue> fields) implements PredicateOperation {
        public Contains { fields = JsonSupport.orderedCopy(fields); }
    }

    record StartsWith(Map<String, JsonValue> fields) implements PredicateOperation {
        public StartsWith { fields = JsonSupport.orderedCopy(fields); }
    }

    record EndsWith(Map<String, JsonValue> fields) implements PredicateOperation {
        public EndsWith { fields = JsonSupport.orderedCopy(fields); }
    }

    record Matches(Map<String, JsonValue> fields) implements PredicateOperation {
        public Matches { fields = JsonSupport.orderedCopy(fields); }
    }

    record Exists(Map<String, JsonValue> fields) implements PredicateOperation {
        public Exists { fields = JsonSupport.orderedCopy(fields); }
    }

    record Not(Predicate predicate) implements PredicateOperation {}

    record Or(List<Predicate> predicates) implements PredicateOperation {
        public Or { predicates = List.copyOf(predicates); }
    }

    record And(List<Predicate> predicates) implements PredicateOperation {
        public And { predicates = List.copyOf(predicates); }
    }

    record Inject(String script) implements PredicateOperation {}

    /** {@link PredicateParameters} keys, reserved so they are never mistaken for the operation tag. */
    Set<String> RESERVED_KEYS = Set.of("caseSensitive", "keyCaseSensitive", "except", "xpath", "jsonpath");

    static PredicateOperation read(JsonObject obj) {
        List<String> opKeys = obj.fields().keySet().stream()
                .filter(k -> !RESERVED_KEYS.contains(k))
                .toList();
        if (opKeys.size() != 1) {
            throw new WireFormatException(
                    "predicate object must have exactly one operation key, found " + opKeys);
        }
        String key = opKeys.get(0);
        JsonValue value = obj.get(key);
        return switch (key) {
            case "equals" -> new Equals(fieldsOf(value, key));
            case "deepEquals" -> new DeepEquals(fieldsOf(value, key));
            case "contains" -> new Contains(fieldsOf(value, key));
            case "startsWith" -> new StartsWith(fieldsOf(value, key));
            case "endsWith" -> new EndsWith(fieldsOf(value, key));
            case "matches" -> new Matches(fieldsOf(value, key));
            case "exists" -> new Exists(fieldsOf(value, key));
            case "not" -> new Not(Predicate.read(JsonSupport.requireObject(value, "not")));
            case "or" -> new Or(predicateListOf(value, "or"));
            case "and" -> new And(predicateListOf(value, "and"));
            case "inject" -> new Inject(requireInjectScript(value));
            default -> throw new WireFormatException("unknown predicate operation '" + key + "'");
        };
    }

    private static Map<String, JsonValue> fieldsOf(JsonValue value, String key) {
        return JsonSupport.requireObject(value, key).fields();
    }

    private static List<Predicate> predicateListOf(JsonValue value, String key) {
        List<Predicate> out = new ArrayList<>();
        for (JsonValue el : JsonSupport.requireArray(value, key).items()) {
            out.add(Predicate.read(JsonSupport.requireObject(el, key + "[]")));
        }
        return out;
    }

    private static String requireInjectScript(JsonValue value) {
        if (value instanceof JsonString s) {
            return s.value();
        }
        throw new WireFormatException("'inject': expected a string, got " + JsonSupport.typeName(value));
    }

    // The tag()/value() pair below use instanceof chains rather than a pattern-matching switch:
    // the latter is still a preview feature at the Java 17 release level this module compiles
    // against (finalized only in Java 21).

    /** The wire tag for this operation, e.g. {@code "equals"} or {@code "not"}. */
    default String tag() {
        if (this instanceof Equals) return "equals";
        if (this instanceof DeepEquals) return "deepEquals";
        if (this instanceof Contains) return "contains";
        if (this instanceof StartsWith) return "startsWith";
        if (this instanceof EndsWith) return "endsWith";
        if (this instanceof Matches) return "matches";
        if (this instanceof Exists) return "exists";
        if (this instanceof Not) return "not";
        if (this instanceof Or) return "or";
        if (this instanceof And) return "and";
        if (this instanceof Inject) return "inject";
        throw new IllegalStateException("unreachable: " + this);
    }

    /** This operation's value, in the shape it takes on the wire under {@link #tag()}. */
    default JsonValue value() {
        if (this instanceof Equals e) return fieldsToJson(e.fields());
        if (this instanceof DeepEquals e) return fieldsToJson(e.fields());
        if (this instanceof Contains e) return fieldsToJson(e.fields());
        if (this instanceof StartsWith e) return fieldsToJson(e.fields());
        if (this instanceof EndsWith e) return fieldsToJson(e.fields());
        if (this instanceof Matches e) return fieldsToJson(e.fields());
        if (this instanceof Exists e) return fieldsToJson(e.fields());
        if (this instanceof Not n) return n.predicate().toJsonValue();
        if (this instanceof Or o) return new JsonArray(o.predicates().stream().map(Predicate::toJsonValue).toList());
        if (this instanceof And a) return new JsonArray(a.predicates().stream().map(Predicate::toJsonValue).toList());
        if (this instanceof Inject i) return new JsonString(i.script());
        throw new IllegalStateException("unreachable: " + this);
    }

    private static JsonValue fieldsToJson(Map<String, JsonValue> fields) {
        JsonObject.Builder builder = JsonObject.builder();
        fields.forEach(builder::put);
        return builder.build();
    }
}
