package io.github.etacassiopeia.rift.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A hand-written recursive-descent JSON parser (RFC 8259). No external dependency, no lenient
 * extensions (no comments, no trailing commas, no unquoted keys) — anything outside the grammar
 * throws {@link JsonParseException} with a 1-based line/column.
 */
final class JsonReader {

    private final String text;
    private int pos;
    private int line = 1;
    private int column = 1;

    JsonReader(String text) {
        this.text = text;
    }

    JsonValue parseDocument() {
        skipWhitespace();
        JsonValue value = parseValue();
        skipWhitespace();
        if (pos < text.length()) {
            throw error("unexpected trailing content after JSON document");
        }
        return value;
    }

    private JsonValue parseValue() {
        if (pos >= text.length()) {
            throw error("unexpected end of input, expected a JSON value");
        }
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> new JsonString(parseStringLiteral());
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield parseNumber();
                }
                throw error("unexpected character '" + c + "'");
            }
        };
    }

    private JsonObject parseObject() {
        expect('{');
        Map<String, JsonValue> fields = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            advance();
            return new JsonObject(fields);
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("expected a string key");
            }
            String key = parseStringLiteral();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            JsonValue value = parseValue();
            fields.put(key, value);
            skipWhitespace();
            char next = requireNext("expected ',' or '}' in object");
            if (next == ',') {
                continue;
            }
            if (next == '}') {
                break;
            }
            throw error("expected ',' or '}' in object, got '" + next + "'");
        }
        return new JsonObject(fields);
    }

    private JsonArray parseArray() {
        expect('[');
        List<JsonValue> items = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            advance();
            return new JsonArray(items);
        }
        while (true) {
            skipWhitespace();
            items.add(parseValue());
            skipWhitespace();
            char next = requireNext("expected ',' or ']' in array");
            if (next == ',') {
                continue;
            }
            if (next == ']') {
                break;
            }
            throw error("expected ',' or ']' in array, got '" + next + "'");
        }
        return new JsonArray(items);
    }

    private String parseStringLiteral() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw error("unterminated string literal");
            }
            char c = advance();
            if (c == '"') {
                break;
            }
            if (c == '\\') {
                sb.append(parseEscape());
            } else if (c < 0x20) {
                throw error("control character in string literal");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private char parseEscape() {
        if (pos >= text.length()) {
            throw error("unterminated escape sequence");
        }
        char c = advance();
        return switch (c) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'u' -> parseUnicodeEscape();
            default -> throw error("invalid escape character '\\" + c + "'");
        };
    }

    private char parseUnicodeEscape() {
        if (pos + 4 > text.length()) {
            throw error("truncated \\u escape");
        }
        String hex = text.substring(pos, pos + 4);
        for (int i = 0; i < 4; i++) {
            advance();
        }
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw error("invalid \\u escape: " + hex);
        }
    }

    private JsonValue parseBoolean() {
        if (text.startsWith("true", pos)) {
            advanceBy(4);
            return JsonBool.TRUE;
        }
        if (text.startsWith("false", pos)) {
            advanceBy(5);
            return JsonBool.FALSE;
        }
        throw error("invalid literal, expected 'true' or 'false'");
    }

    private JsonValue parseNull() {
        if (text.startsWith("null", pos)) {
            advanceBy(4);
            return JsonNull.INSTANCE;
        }
        throw error("invalid literal, expected 'null'");
    }

    private JsonNumber parseNumber() {
        int start = pos;
        if (peek() == '-') {
            advance();
        }
        if (pos >= text.length() || !Character.isDigit(text.charAt(pos))) {
            throw error("invalid number: expected a digit");
        }
        if (text.charAt(pos) == '0') {
            advance();
        } else {
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                advance();
            }
        }
        if (pos < text.length() && text.charAt(pos) == '.') {
            advance();
            if (pos >= text.length() || !Character.isDigit(text.charAt(pos))) {
                throw error("invalid number: expected a digit after '.'");
            }
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                advance();
            }
        }
        if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
            advance();
            if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                advance();
            }
            if (pos >= text.length() || !Character.isDigit(text.charAt(pos))) {
                throw error("invalid number: expected a digit in exponent");
            }
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                advance();
            }
        }
        return new JsonNumber(text.substring(start, pos));
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                advance();
            } else {
                break;
            }
        }
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("unexpected end of input");
        }
        return text.charAt(pos);
    }

    private char advance() {
        char c = text.charAt(pos);
        pos++;
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }

    private void advanceBy(int n) {
        for (int i = 0; i < n; i++) {
            advance();
        }
    }

    private void expect(char c) {
        if (pos >= text.length() || text.charAt(pos) != c) {
            throw error("expected '" + c + "'");
        }
        advance();
    }

    private char requireNext(String message) {
        if (pos >= text.length()) {
            throw error(message);
        }
        return advance();
    }

    private JsonParseException error(String message) {
        return new JsonParseException(message, line, column);
    }
}
