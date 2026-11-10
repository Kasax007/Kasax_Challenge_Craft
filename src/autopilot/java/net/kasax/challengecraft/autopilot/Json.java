package net.kasax.challengecraft.autopilot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer: the autopilot runs on a bare Java without libraries. Objects
 * become {@link LinkedHashMap}s (the order kept), arrays {@link ArrayList}s, numbers {@link Long}
 * (written without fraction or exponent) or {@link Double}, and true, false and null themselves.
 */
final class Json {
    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    /** The value in {@code text}; IllegalArgumentException when it is not JSON. */
    static Object parse(String text) {
        Json p = new Json(text);
        p.space();
        Object v = p.value();
        p.space();
        if (p.i != p.s.length()) throw p.error("text after the value");
        return v;
    }

    // ---- reading ------------------------------------------------------------------------------

    private Object value() {
        if (i >= s.length()) throw error("the text ends");
        char c = s.charAt(i);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                return word("true", Boolean.TRUE);
            case 'f':
                return word("false", Boolean.FALSE);
            case 'n':
                return word("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return number();
                throw error("unexpected '" + c + "'");
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> out = new LinkedHashMap<>();
        i++;
        space();
        if (peek() == '}') {
            i++;
            return out;
        }
        while (true) {
            space();
            if (peek() != '"') throw error("a key expected");
            String key = string();
            space();
            expect(':');
            space();
            out.put(key, value());
            space();
            char c = next();
            if (c == '}') return out;
            if (c != ',') throw error("',' or '}' expected");
        }
    }

    private List<Object> array() {
        List<Object> out = new ArrayList<>();
        i++;
        space();
        if (peek() == ']') {
            i++;
            return out;
        }
        while (true) {
            space();
            out.add(value());
            space();
            char c = next();
            if (c == ']') return out;
            if (c != ',') throw error("',' or ']' expected");
        }
    }

    private String string() {
        expect('"');
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') return b.toString();
            if (c != '\\') {
                b.append(c);
                continue;
            }
            char e = next();
            switch (e) {
                case 'n' -> b.append('\n');
                case 't' -> b.append('\t');
                case 'r' -> b.append('\r');
                case 'b' -> b.append('\b');
                case 'f' -> b.append('\f');
                case 'u' -> {
                    if (i + 4 > s.length()) throw error("a short \\u escape");
                    b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                }
                default -> b.append(e); // \" \\ \/
            }
        }
    }

    private Object number() {
        int start = i;
        if (peek() == '-') i++;
        boolean fraction = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                i++;
            } else if (c == '.' || c == 'e' || c == 'E') {
                fraction = true;
                i++;
                // (The sign of an exponent: 1e-5.)
                if (c != '.' && i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
            } else {
                break;
            }
        }
        String t = s.substring(start, i);
        try {
            if (!fraction) {
                try {
                    return Long.parseLong(t);
                } catch (NumberFormatException tooLong) {
                    return Double.parseDouble(t);
                }
            }
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            throw error("not a number: " + t);
        }
    }

    private Object word(String w, Object v) {
        if (!s.startsWith(w, i)) throw error(w + " expected");
        i += w.length();
        return v;
    }

    private void space() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private char peek() {
        return i < s.length() ? s.charAt(i) : '\0';
    }

    private char next() {
        if (i >= s.length()) throw error("the text ends");
        return s.charAt(i++);
    }

    private void expect(char c) {
        if (next() != c) throw error("'" + c + "' expected");
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("JSON: " + what + " at " + i);
    }

    // ---- writing ------------------------------------------------------------------------------

    /** On one line. */
    static String write(Object v) {
        StringBuilder b = new StringBuilder();
        write(b, v, -1, 0);
        return b.toString();
    }

    /** Indented by one space a level, as the laptop trainer writes its files (Python's indent=1). */
    static String pretty(Object v) {
        StringBuilder b = new StringBuilder();
        write(b, v, 1, 0);
        return b.append('\n').toString();
    }

    private static void write(StringBuilder b, Object v, int indent, int depth) {
        if (v == null) {
            b.append("null");
        } else if (v instanceof String str) {
            quote(b, str);
        } else if (v instanceof Boolean bool) {
            b.append(bool);
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            // (No exponents, as Python writes them: 1792155750.66, not 1.79215575066E9.)
            b.append(Double.isFinite(d) ? BigDecimal.valueOf(d).toPlainString() : "null");
        } else if (v instanceof Number n) {
            b.append(n.longValue());
        } else if (v instanceof Map<?, ?> m) {
            b.append('{');
            int k = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (k++ > 0) b.append(',');
                newline(b, indent, depth + 1);
                quote(b, String.valueOf(e.getKey()));
                b.append(indent >= 0 ? ": " : ":");
                write(b, e.getValue(), indent, depth + 1);
            }
            if (k > 0) newline(b, indent, depth);
            b.append('}');
        } else if (v instanceof Iterable<?> list) {
            b.append('[');
            int k = 0;
            for (Object x : list) {
                if (k++ > 0) b.append(',');
                newline(b, indent, depth + 1);
                write(b, x, indent, depth + 1);
            }
            if (k > 0) newline(b, indent, depth);
            b.append(']');
        } else {
            quote(b, v.toString());
        }
    }

    private static void newline(StringBuilder b, int indent, int depth) {
        if (indent < 0) return;
        b.append('\n');
        for (int k = 0; k < indent * depth; k++) b.append(' ');
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        b.append('"');
    }

    // ---- reading values out of parsed JSON ------------------------------------------------------

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object v) {
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object v) {
        return v instanceof List<?> l ? (List<Object>) l : new ArrayList<>();
    }

    static double num(Object v, double fallback) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String str) {
            try {
                return Double.parseDouble(str.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    static String str(Object v, String fallback) {
        return v == null ? fallback : v.toString();
    }
}
