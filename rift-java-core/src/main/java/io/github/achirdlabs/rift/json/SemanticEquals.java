package io.github.achirdlabs.rift.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Implements {@link JsonValue#semanticEquals}; see that method for the contract. */
final class SemanticEquals {

    private SemanticEquals() {}

    static boolean equals(JsonValue a, JsonValue b, String fieldKey) {
        if (a == null || b == null) {
            return a == b;
        }
        if ("statusCode".equals(fieldKey)) {
            return statusCodeEquals(a, b);
        }
        if (a instanceof JsonObject oa && b instanceof JsonObject ob) {
            return objectEquals(oa, ob);
        }
        if (a instanceof JsonArray aa && b instanceof JsonArray ab) {
            return arrayEquals(aa, ab);
        }
        if (a instanceof JsonString sa && b instanceof JsonString sb) {
            return sa.value().equals(sb.value());
        }
        if (a instanceof JsonNumber na && b instanceof JsonNumber nb) {
            return na.raw().equals(nb.raw());
        }
        if (a instanceof JsonBool ba && b instanceof JsonBool bb) {
            return ba.value() == bb.value();
        }
        if (a instanceof JsonNull && b instanceof JsonNull) {
            return true;
        }
        return false; // different JSON types
    }

    private static boolean objectEquals(JsonObject a, JsonObject b) {
        Map<String, JsonValue> ma = a.fields();
        Map<String, JsonValue> mb = b.fields();
        if (!ma.keySet().equals(mb.keySet())) {
            return false;
        }
        for (Map.Entry<String, JsonValue> e : ma.entrySet()) {
            String key = e.getKey();
            JsonValue va = e.getValue();
            JsonValue vb = mb.get(key);
            if ("headers".equals(key) && va instanceof JsonObject ha && vb instanceof JsonObject hb) {
                if (!headersEqual(ha, hb)) {
                    return false;
                }
            } else if (!equals(va, vb, key)) {
                return false;
            }
        }
        return true;
    }

    private static boolean headersEqual(JsonObject a, JsonObject b) {
        if (!a.fields().keySet().equals(b.fields().keySet())) {
            return false;
        }
        for (Map.Entry<String, JsonValue> e : a.fields().entrySet()) {
            if (!headerValueEquals(e.getValue(), b.fields().get(e.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /** A single header value {@code "x"} and a one-element array {@code ["x"]} compare equal. */
    private static boolean headerValueEquals(JsonValue a, JsonValue b) {
        List<String> la = headerStrings(a);
        List<String> lb = headerStrings(b);
        if (la != null && lb != null) {
            return la.equals(lb);
        }
        return equals(a, b, null); // not a string/string-array shape: fall back to exact equality
    }

    private static List<String> headerStrings(JsonValue v) {
        if (v instanceof JsonString s) {
            return List.of(s.value());
        }
        if (v instanceof JsonArray arr) {
            List<String> out = new ArrayList<>();
            for (JsonValue el : arr.items()) {
                if (el instanceof JsonString s) {
                    out.add(s.value());
                } else {
                    return null;
                }
            }
            return out;
        }
        return null;
    }

    private static boolean arrayEquals(JsonArray a, JsonArray b) {
        List<JsonValue> la = a.items();
        List<JsonValue> lb = b.items();
        if (la.size() != lb.size()) {
            return false;
        }
        for (int i = 0; i < la.size(); i++) {
            if (!equals(la.get(i), lb.get(i), null)) {
                return false;
            }
        }
        return true;
    }

    /** A {@code statusCode} number and its string form compare equal (200 == "200"). */
    private static boolean statusCodeEquals(JsonValue a, JsonValue b) {
        String sa = statusCodeString(a);
        String sb = statusCodeString(b);
        return sa != null && sa.equals(sb);
    }

    private static String statusCodeString(JsonValue v) {
        if (v instanceof JsonString s) {
            return s.value();
        }
        if (v instanceof JsonNumber n) {
            return n.raw();
        }
        return null;
    }
}
