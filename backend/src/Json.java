import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny JSON reader/writer so the project needs no external libraries.
 *
 * parse():  JSON text  -> Map (object), List (array), String, Double, Boolean or null
 * toJson(): Map / List / String / Number / Boolean / null -> JSON text
 */
public class Json {

    // ------------------------------------------------------------------
    // Reading JSON
    // ------------------------------------------------------------------

    private final String text;
    private int pos = 0;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json parser = new Json(text);
        Object value = parser.readValue(0);
        parser.skipSpaces();
        if (parser.pos < text.length()) {
            throw new IllegalArgumentException("Extra characters after JSON");
        }
        return value;
    }

    private Object readValue(int depth) {
        if (depth > 20) {
            throw new IllegalArgumentException("JSON is nested too deeply");
        }
        skipSpaces();
        char c = peek();
        if (c == '{') return readObject(depth);
        if (c == '[') return readArray(depth);
        if (c == '"') return readString();
        if (text.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
        if (text.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
        if (text.startsWith("null", pos)) { pos += 4; return null; }
        return readNumber();
    }

    private Map<String, Object> readObject(int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // skip {
        skipSpaces();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipSpaces();
            if (peek() != '"') {
                throw new IllegalArgumentException("Expected a quoted key");
            }
            String key = readString();
            skipSpaces();
            expect(':');
            map.put(key, readValue(depth + 1));
            skipSpaces();
            if (peek() == ',') {
                pos++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> readArray(int depth) {
        List<Object> list = new ArrayList<>();
        pos++; // skip [
        skipSpaces();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(readValue(depth + 1));
            skipSpaces();
            if (peek() == ',') {
                pos++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String readString() {
        StringBuilder sb = new StringBuilder();
        pos++; // skip opening quote
        while (true) {
            if (pos >= text.length()) {
                throw new IllegalArgumentException("Unterminated string");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char next = text.charAt(pos++);
            switch (next) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'u':
                    sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default: sb.append(next); // handles \" \\ and \/
            }
        }
    }

    private Double readNumber() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        if (start == pos) {
            throw new IllegalArgumentException("Unexpected character in JSON");
        }
        return Double.valueOf(text.substring(start, pos));
    }

    private void skipSpaces() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= text.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON");
        }
        return text.charAt(pos);
    }

    private void expect(char c) {
        if (peek() != c) {
            throw new IllegalArgumentException("Expected '" + c + "'");
        }
        pos++;
    }

    // ------------------------------------------------------------------
    // Writing JSON
    // ------------------------------------------------------------------

    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            quote(sb, (String) v);
        } else if (v instanceof BigDecimal) {
            sb.append(((BigDecimal) v).toPlainString());
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object item : (List<?>) v) {
                if (!first) sb.append(',');
                first = false;
                write(sb, item);
            }
            sb.append(']');
        } else {
            quote(sb, v.toString());
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
