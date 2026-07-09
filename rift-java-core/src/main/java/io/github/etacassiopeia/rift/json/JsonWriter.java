package io.github.etacassiopeia.rift.json;

import java.util.Iterator;
import java.util.Map;

/** Serializes a {@link JsonValue} tree back to text, preserving object insertion order. */
final class JsonWriter {

    private JsonWriter() {}

    static String compact(JsonValue value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    static String pretty(JsonValue value) {
        StringBuilder sb = new StringBuilder();
        writePretty(value, sb, 0);
        return sb.toString();
    }

    // Note: these dispatch on JsonValue's runtime type via instanceof chains rather than a
    // pattern-matching switch — the latter is still a preview feature at the Java 17 release
    // level this module compiles against (finalized only in Java 21).
    private static void write(JsonValue value, StringBuilder sb) {
        if (value instanceof JsonObject obj) {
            sb.append('{');
            Iterator<Map.Entry<String, JsonValue>> it = obj.fields().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, JsonValue> e = it.next();
                writeString(e.getKey(), sb);
                sb.append(':');
                write(e.getValue(), sb);
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            sb.append('}');
        } else if (value instanceof JsonArray arr) {
            sb.append('[');
            Iterator<JsonValue> it = arr.items().iterator();
            while (it.hasNext()) {
                write(it.next(), sb);
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            sb.append(']');
        } else if (value instanceof JsonString s) {
            writeString(s.value(), sb);
        } else if (value instanceof JsonNumber n) {
            sb.append(n.raw());
        } else if (value instanceof JsonBool b) {
            sb.append(b.value());
        } else {
            sb.append("null");
        }
    }

    private static void writePretty(JsonValue value, StringBuilder sb, int depth) {
        if (value instanceof JsonObject obj) {
            if (obj.fields().isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            Iterator<Map.Entry<String, JsonValue>> it = obj.fields().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, JsonValue> e = it.next();
                indent(sb, depth + 1);
                writeString(e.getKey(), sb);
                sb.append(": ");
                writePretty(e.getValue(), sb, depth + 1);
                if (it.hasNext()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            indent(sb, depth);
            sb.append('}');
        } else if (value instanceof JsonArray arr) {
            if (arr.items().isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            Iterator<JsonValue> it = arr.items().iterator();
            while (it.hasNext()) {
                indent(sb, depth + 1);
                writePretty(it.next(), sb, depth + 1);
                if (it.hasNext()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            indent(sb, depth);
            sb.append(']');
        } else {
            write(value, sb);
        }
    }

    private static void indent(StringBuilder sb, int depth) {
        sb.append("  ".repeat(depth));
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
