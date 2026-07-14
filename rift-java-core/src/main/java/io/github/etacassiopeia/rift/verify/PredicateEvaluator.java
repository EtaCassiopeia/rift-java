package io.github.etacassiopeia.rift.verify;

import io.github.etacassiopeia.rift.RecordedRequest;
import io.github.etacassiopeia.rift.error.InvalidDefinition;
import io.github.etacassiopeia.rift.json.JsonBool;
import io.github.etacassiopeia.rift.json.JsonNull;
import io.github.etacassiopeia.rift.json.JsonNumber;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.Predicate;
import io.github.etacassiopeia.rift.model.PredicateOperation;
import io.github.etacassiopeia.rift.model.PredicateParameters;
import io.github.etacassiopeia.rift.model.PredicateSelector;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Client-side evaluation of Mountebank predicate semantics against a recorded request, used to
 * verify what an imposter actually received without round-tripping the comparison to the engine.
 *
 * <p>Uses {@code instanceof} chains rather than pattern-matching {@code switch} throughout: the
 * latter is still a preview feature at the Java 17 release level this module compiles against
 * (finalized only in Java 21).
 */
public final class PredicateEvaluator {

    private PredicateEvaluator() {}

    /** Whether every predicate in {@code predicates} matches {@code request} (logical AND). */
    public static boolean matches(RecordedRequest request, List<Predicate> predicates) {
        for (Predicate predicate : predicates) {
            if (!matchesOne(request, predicate)) {
                return false;
            }
        }
        return true;
    }

    /** The count of top-level {@code predicates} that individually match {@code request}. */
    static int satisfiedClauses(RecordedRequest request, List<Predicate> predicates) {
        int count = 0;
        for (Predicate predicate : predicates) {
            try {
                if (matchesOne(request, predicate)) {
                    count++;
                }
            } catch (RuntimeException e) {
                // Ranking a near-miss for the failure message must never throw a *different* error than
                // verify() itself would; a clause that can't be evaluated just doesn't count toward rank.
            }
        }
        return count;
    }

    /** The first top-level predicate that fails to match, described as a diff-friendly {@link Failure}. */
    static Optional<Failure> firstFailure(RecordedRequest request, List<Predicate> predicates) {
        for (Predicate predicate : predicates) {
            if (!matchesOne(request, predicate)) {
                return Optional.of(describeFailure(request, predicate));
            }
        }
        return Optional.empty();
    }

    /** One failing clause: the field it applies to, the operation, and the expected/actual values. */
    record Failure(String field, String op, String expected, String actual) {}

    private static boolean matchesOne(RecordedRequest request, Predicate predicate) {
        PredicateOperation op = predicate.operation();
        if (op instanceof PredicateOperation.Not not) {
            return !matches(request, List.of(not.predicate()));
        }
        if (op instanceof PredicateOperation.Or or) {
            return or.predicates().stream().anyMatch(p -> matches(request, List.of(p)));
        }
        if (op instanceof PredicateOperation.And and) {
            return and.predicates().stream().allMatch(p -> matches(request, List.of(p)));
        }
        if (op instanceof PredicateOperation.Inject) {
            throw new InvalidDefinition("inject predicates cannot be verified client-side");
        }
        return matchesFieldOp(request, predicate.parameters(), op);
    }

    // ------------------------------------------------------------------
    // Field operations (equals/deepEquals/contains/startsWith/endsWith/matches/exists)
    // ------------------------------------------------------------------

    private static boolean matchesFieldOp(RecordedRequest request, PredicateParameters params, PredicateOperation op) {
        Map<String, JsonValue> fields = fieldsOf(op);
        String opTag = op.tag();
        if (params.selector().isPresent()) {
            JsonValue expected = fields.isEmpty() ? JsonBool.TRUE : fields.values().iterator().next();
            return evalSelector(request, params, opTag, params.selector().get(), expected).matched();
        }
        for (var entry : fields.entrySet()) {
            if (!evalField(request, params, opTag, entry.getKey(), entry.getValue()).matched()) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, JsonValue> fieldsOf(PredicateOperation op) {
        if (op instanceof PredicateOperation.Equals e) return e.fields();
        if (op instanceof PredicateOperation.DeepEquals e) return e.fields();
        if (op instanceof PredicateOperation.Contains e) return e.fields();
        if (op instanceof PredicateOperation.StartsWith e) return e.fields();
        if (op instanceof PredicateOperation.EndsWith e) return e.fields();
        if (op instanceof PredicateOperation.Matches e) return e.fields();
        if (op instanceof PredicateOperation.Exists e) return e.fields();
        throw new IllegalStateException("not a field-comparison predicate operation: " + op);
    }

    private record FieldEval(boolean matched, String actualDisplay) {}

    private static FieldEval evalField(
            RecordedRequest request, PredicateParameters params, String opTag, String fieldName, JsonValue expected) {
        if ("method".equals(fieldName)) {
            return evalScalar(request.method(), expected, params, opTag);
        }
        if ("path".equals(fieldName)) {
            return evalScalar(request.path(), expected, params, opTag);
        }
        if ("body".equals(fieldName)) {
            return evalScalar(request.body(), expected, params, opTag);
        }
        if ("query".equals(fieldName)) {
            return evalMulti(request.query(), expected, params, opTag);
        }
        if ("headers".equals(fieldName)) {
            return evalMulti(request.headers(), expected, params, opTag);
        }
        // an unrecognized field name never matches (rather than silently no-op-passing)
        return new FieldEval(false, "");
    }

    private static FieldEval evalScalar(String actual, JsonValue expected, PredicateParameters params, String opTag) {
        String display = actual == null ? "" : actual;
        if ("exists".equals(opTag)) {
            boolean wantExists = isTrue(expected);
            boolean present = actual != null && !actual.isEmpty();
            return new FieldEval(wantExists == present, display);
        }
        return new FieldEval(matchesOp(opTag, actual == null ? "" : actual, expected, params), display);
    }

    private static FieldEval evalMulti(
            Map<String, List<String>> multiMap, JsonValue expectedField, PredicateParameters params, String opTag) {
        if (!(expectedField instanceof JsonObject nested)) {
            return new FieldEval(false, "");
        }
        boolean keyCaseSensitive = params.keyCaseSensitive().orElse(false);
        String lastActualDisplay = "";
        for (var entry : nested.fields().entrySet()) {
            List<String> actualValues = lookupMulti(multiMap, entry.getKey(), keyCaseSensitive);
            lastActualDisplay = actualValues == null || actualValues.isEmpty() ? "" : String.join(", ", actualValues);
            if ("exists".equals(opTag)) {
                boolean wantExists = isTrue(entry.getValue());
                boolean present = actualValues != null && actualValues.stream().anyMatch(v -> !v.isEmpty());
                if (wantExists != present) {
                    return new FieldEval(false, lastActualDisplay);
                }
            } else {
                JsonValue expectedValue = entry.getValue();
                boolean any = actualValues != null
                        && actualValues.stream().anyMatch(v -> matchesOp(opTag, v, expectedValue, params));
                if (!any) {
                    return new FieldEval(false, lastActualDisplay);
                }
            }
        }
        return new FieldEval(true, lastActualDisplay);
    }

    private static List<String> lookupMulti(Map<String, List<String>> map, String name, boolean keyCaseSensitive) {
        if (keyCaseSensitive) {
            return map.get(name);
        }
        for (var entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean isTrue(JsonValue value) {
        return value instanceof JsonBool b && b.value();
    }

    /** Compares {@code actualRaw} (after stripping {@code except} matches) against {@code expected} per {@code opTag}. */
    private static boolean matchesOp(String opTag, String actualRaw, JsonValue expected, PredicateParameters params) {
        String actual = applyExcept(actualRaw, params.except());
        boolean caseSensitive = params.caseSensitive().orElse(false);
        if ("deepEquals".equals(opTag)) {
            return deepEqualsMatch(actual, expected, caseSensitive);
        }
        String expectedStr = jsonToCompareString(expected);
        if ("matches".equals(opTag)) {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            return Pattern.compile(expectedStr, flags).matcher(actual).find();
        }
        expectedStr = applyExcept(expectedStr, params.except()); // Mountebank strips `except` from both sides
        if (!caseSensitive) {
            actual = actual.toLowerCase(Locale.ROOT);
            expectedStr = expectedStr.toLowerCase(Locale.ROOT);
        }
        if ("equals".equals(opTag)) return actual.equals(expectedStr);
        if ("contains".equals(opTag)) return actual.contains(expectedStr);
        if ("startsWith".equals(opTag)) return actual.startsWith(expectedStr);
        if ("endsWith".equals(opTag)) return actual.endsWith(expectedStr);
        throw new IllegalStateException("unsupported field-comparison operation: " + opTag);
    }

    private static String applyExcept(String actual, String exceptRegex) {
        if (exceptRegex == null || exceptRegex.isEmpty()) {
            return actual;
        }
        return actual.replaceAll(exceptRegex, "");
    }

    private static String jsonToCompareString(JsonValue value) {
        return value instanceof JsonString s ? s.value() : value.toJson();
    }

    private static boolean deepEqualsMatch(String actualRaw, JsonValue expected, boolean caseSensitive) {
        Optional<JsonValue> actualJson = tryParseJson(actualRaw);
        JsonValue expectedValue = expected instanceof JsonString es ? tryParseJson(es.value()).orElse(expected) : expected;
        if (actualJson.isPresent()) {
            JsonValue a = caseSensitive ? actualJson.get() : lowerLeaves(actualJson.get());
            JsonValue e = caseSensitive ? expectedValue : lowerLeaves(expectedValue);
            return JsonValue.semanticEquals(a, e);
        }
        // non-JSON body: string fallback
        String exp = expected instanceof JsonString es2 ? es2.value() : expected.toJson();
        return caseSensitive ? actualRaw.equals(exp) : actualRaw.equalsIgnoreCase(exp);
    }

    /** Lowercases string leaf values for case-insensitive deepEquals (keys/numbers/booleans unchanged). */
    private static JsonValue lowerLeaves(JsonValue v) {
        if (v instanceof JsonString s) {
            return new JsonString(s.value().toLowerCase(Locale.ROOT));
        }
        if (v instanceof JsonArray arr) {
            return new JsonArray(arr.items().stream().map(PredicateEvaluator::lowerLeaves).toList());
        }
        if (v instanceof JsonObject obj) {
            JsonObject.Builder b = JsonObject.builder();
            obj.fields().forEach((k, val) -> b.put(k, lowerLeaves(val)));
            return b.build();
        }
        return v;
    }

    private static Optional<JsonValue> tryParseJson(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(JsonValue.parse(text));
        } catch (io.github.etacassiopeia.rift.json.JsonParseException e) {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------
    // Structured selectors (JsonPath / XPath) over the request body
    // ------------------------------------------------------------------

    private static FieldEval evalSelector(
            RecordedRequest request, PredicateParameters params, String opTag, PredicateSelector selector, JsonValue expected) {
        List<String> extracted = extractValues(request, selector);
        String display = String.join(", ", extracted);
        if ("exists".equals(opTag)) {
            boolean wantExists = isTrue(expected);
            boolean present = extracted.stream().anyMatch(v -> !v.isEmpty());
            return new FieldEval(wantExists == present, display);
        }
        boolean any = extracted.stream().anyMatch(v -> matchesOp(opTag, v, expected, params));
        return new FieldEval(any, display);
    }

    private static List<String> extractValues(RecordedRequest request, PredicateSelector selector) {
        if (selector instanceof PredicateSelector.JsonPath jsonPath) {
            return request.bodyAsJson()
                    .map(body -> evaluateJsonPath(body, jsonPath.selector()).stream().map(PredicateEvaluator::stringify).toList())
                    .orElseGet(List::of);
        }
        if (selector instanceof PredicateSelector.XPath xPath) {
            return extractXPath(request, xPath);
        }
        throw new IllegalStateException("unreachable: " + selector);
    }

    private static String stringify(JsonValue value) {
        if (value instanceof JsonString s) return s.value();
        if (value instanceof JsonNumber n) return n.raw();
        if (value instanceof JsonBool b) return Boolean.toString(b.value());
        if (value instanceof JsonNull) return "";
        return value.toJson();
    }

    /** A subset JSONPath evaluator: {@code $} root, {@code .child}, {@code [index]}, {@code [*]}, {@code ..} recursive descent. */
    private static List<JsonValue> evaluateJsonPath(JsonValue root, String path) {
        if (!path.startsWith("$")) {
            throw new IllegalArgumentException("jsonpath must start with '$': " + path);
        }
        List<JsonValue> current = List.of(root);
        int i = 1;
        while (i < path.length()) {
            char c = path.charAt(i);
            if (path.startsWith("..", i)) {
                i += 2;
                int start = i;
                while (i < path.length() && path.charAt(i) != '.' && path.charAt(i) != '[') {
                    i++;
                }
                String name = path.substring(start, i);
                List<JsonValue> next = new ArrayList<>();
                for (JsonValue v : current) {
                    collectRecursive(v, name, next);
                }
                current = next;
            } else if (c == '.') {
                i++;
                int start = i;
                while (i < path.length() && path.charAt(i) != '.' && path.charAt(i) != '[') {
                    i++;
                }
                String name = path.substring(start, i);
                List<JsonValue> next = new ArrayList<>();
                for (JsonValue v : current) {
                    if (v instanceof JsonObject obj && obj.has(name)) {
                        next.add(obj.get(name));
                    }
                }
                current = next;
            } else if (c == '[') {
                int end = path.indexOf(']', i);
                if (end < 0) {
                    throw new IllegalArgumentException("unterminated '[' in jsonpath: " + path);
                }
                String inside = path.substring(i + 1, end);
                i = end + 1;
                List<JsonValue> next = new ArrayList<>();
                if ("*".equals(inside)) {
                    for (JsonValue v : current) {
                        if (v instanceof JsonArray arr) next.addAll(arr.items());
                        else if (v instanceof JsonObject obj) next.addAll(obj.fields().values());
                    }
                } else {
                    int idx = Integer.parseInt(inside);
                    for (JsonValue v : current) {
                        if (v instanceof JsonArray arr && idx >= 0 && idx < arr.items().size()) {
                            next.add(arr.items().get(idx));
                        }
                    }
                }
                current = next;
            } else {
                throw new IllegalArgumentException("invalid jsonpath at index " + i + ": " + path);
            }
        }
        return current;
    }

    private static void collectRecursive(JsonValue v, String name, List<JsonValue> out) {
        if (v instanceof JsonObject obj) {
            if (name.isEmpty()) {
                out.add(v);
            } else if (obj.has(name)) {
                out.add(obj.get(name));
            }
            for (JsonValue child : obj.fields().values()) {
                collectRecursive(child, name, out);
            }
        } else if (v instanceof JsonArray arr) {
            if (name.isEmpty()) {
                out.add(v);
            }
            for (JsonValue child : arr.items()) {
                collectRecursive(child, name, out);
            }
        } else if (name.isEmpty()) {
            out.add(v);
        }
    }

    private static List<String> extractXPath(RecordedRequest request, PredicateSelector.XPath xPath) {
        String body = request.body();
        if (body == null || body.isBlank()) {
            return List.of();
        }
        Document doc;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            // Harden against XXE: the body is attacker-influenced recorded traffic parsed in the test JVM.
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            DocumentBuilder builder = dbf.newDocumentBuilder();
            doc = builder.parse(new InputSource(new StringReader(body)));
        } catch (javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException | java.io.IOException e) {
            // a non-XML (or entity-laden) body simply doesn't match this xpath predicate
            return List.of();
        }
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            xPath.namespaces().ifPresent(ns -> xpath.setNamespaceContext(new MapNamespaceContext(ns)));
            NodeList nodes = (NodeList) xpath.evaluate(xPath.selector(), doc, XPathConstants.NODESET);
            List<String> out = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                out.add(nodes.item(i).getTextContent());
            }
            return out;
        } catch (javax.xml.xpath.XPathExpressionException e) {
            // a malformed xpath SELECTOR is a bad predicate (like a bad regex), not a non-matching request
            throw new io.github.etacassiopeia.rift.error.InvalidDefinition(
                    "invalid xpath selector \"" + xPath.selector() + "\": " + e.getMessage());
        }
    }

    private static final class MapNamespaceContext implements NamespaceContext {
        private final Map<String, String> prefixToUri;

        MapNamespaceContext(Map<String, String> prefixToUri) {
            this.prefixToUri = prefixToUri;
        }

        @Override
        public String getNamespaceURI(String prefix) {
            return prefixToUri.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
        }

        @Override
        public String getPrefix(String namespaceURI) {
            for (var entry : prefixToUri.entrySet()) {
                if (entry.getValue().equals(namespaceURI)) {
                    return entry.getKey();
                }
            }
            return null;
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            List<String> out = new ArrayList<>();
            for (var entry : prefixToUri.entrySet()) {
                if (entry.getValue().equals(namespaceURI)) {
                    out.add(entry.getKey());
                }
            }
            return out.iterator();
        }
    }

    // ------------------------------------------------------------------
    // Diff description for VerificationException
    // ------------------------------------------------------------------

    private static Failure describeFailure(RecordedRequest request, Predicate predicate) {
        PredicateOperation op = predicate.operation();
        if (op instanceof PredicateOperation.Not not) {
            // the negated predicate matched (that's why "not" failed); describe that clause
            Failure inner = describeFailureAssumingMatch(request, not.predicate());
            return new Failure(inner.field(), "not " + inner.op(), inner.expected(), inner.actual());
        }
        if (op instanceof PredicateOperation.And and) {
            for (Predicate p : and.predicates()) {
                if (!matches(request, List.of(p))) {
                    return describeFailure(request, p);
                }
            }
            return new Failure("and", "and", "all clauses to match", "not all matched");
        }
        if (op instanceof PredicateOperation.Or or) {
            if (or.predicates().isEmpty()) {
                return new Failure("or", "or", "any clause to match", "no clauses");
            }
            return describeFailure(request, or.predicates().get(0));
        }
        if (op instanceof PredicateOperation.Inject) {
            throw new InvalidDefinition("inject predicates cannot be verified client-side");
        }
        return describeFieldFailure(request, predicate.parameters(), op);
    }

    /** Describes a predicate that is known to currently match (used to explain a failing {@code not}). */
    private static Failure describeFailureAssumingMatch(RecordedRequest request, Predicate predicate) {
        PredicateOperation op = predicate.operation();
        if (op instanceof PredicateOperation.Not || op instanceof PredicateOperation.And || op instanceof PredicateOperation.Or) {
            return new Failure("not", op.tag(), "no match", "matched");
        }
        if (op instanceof PredicateOperation.Inject) {
            throw new InvalidDefinition("inject predicates cannot be verified client-side");
        }
        return describeFieldFailure(request, predicate.parameters(), op);
    }

    private static Failure describeFieldFailure(RecordedRequest request, PredicateParameters params, PredicateOperation op) {
        Map<String, JsonValue> fields = fieldsOf(op);
        String opTag = op.tag();
        if (params.selector().isPresent()) {
            JsonValue expected = fields.isEmpty() ? JsonBool.TRUE : fields.values().iterator().next();
            PredicateSelector selector = params.selector().get();
            FieldEval eval = evalSelector(request, params, opTag, selector, expected);
            String selectorText = selector instanceof PredicateSelector.JsonPath jp ? jp.selector()
                    : ((PredicateSelector.XPath) selector).selector();
            return new Failure("body[" + selectorText + "]", opTag, jsonToCompareString(expected), eval.actualDisplay());
        }
        for (var entry : fields.entrySet()) {
            FieldEval eval = evalField(request, params, opTag, entry.getKey(), entry.getValue());
            if (!eval.matched()) {
                String field = entry.getKey();
                JsonValue expectedValue = entry.getValue();
                String expectedDisplay = "query".equals(field) || "headers".equals(field)
                        ? entry.getValue().toJson()
                        : jsonToCompareString(expectedValue);
                return new Failure(field, opTag, expectedDisplay, eval.actualDisplay());
            }
        }
        // every field matched individually — should not happen since the caller already checked this predicate failed
        return new Failure(opTag, opTag, "?", "?");
    }
}
