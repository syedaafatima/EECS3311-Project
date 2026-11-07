package ca.yorku.careeragent.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small strict JSON codec for the provider boundary; never parses JSON with regex. */
public final class Json {
    private Json() { }

    public static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : text.toCharArray()) {
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 32) out.append(String.format("\\u%04x", (int) c));
                        else out.append(c);
                    }
                }
            }
            return out.append('"').toString();
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().map(e -> encode(e.getKey().toString()) + ":" + encode(e.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Iterable<?> list) {
            List<String> items = new ArrayList<>();
            list.forEach(item -> items.add(encode(item)));
            return String.join(",", items).transform(s -> "[" + s + "]");
        }
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        throw new IllegalArgumentException("Unsupported JSON value.");
    }

    public static Object parse(String text) {
        if (text == null || text.length() > 1_048_576) throw invalid();
        Reader reader = new Reader(text);
        Object value = reader.value(0);
        reader.space();
        if (reader.pos != text.length()) throw invalid();
        return value;
    }

    public static Map<?, ?> object(Object value) {
        if (value instanceof Map<?, ?> map) return map;
        throw invalid();
    }

    public static List<?> array(Object value) {
        if (value instanceof List<?> list) return list;
        throw invalid();
    }

    public static String text(Object value) {
        if (value instanceof String text && !text.isBlank()) return text.strip();
        throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid JSON response. Retry the task or enter information manually.");
    }

    private static final class Reader {
        private final String text;
        private int pos;
        Reader(String text) { this.text = text; }
        void space() {
            while (pos < text.length() && " \n\r\t".indexOf(text.charAt(pos)) >= 0) pos++;
        }
        boolean take(char c) {
            space();
            if (pos < text.length() && text.charAt(pos) == c) { pos++; return true; }
            return false;
        }
        Object value(int depth) {
            space();
            if (depth > 30 || pos == text.length()) throw invalid();
            char c = text.charAt(pos);
            if (c == '"') return string();
            if (take('{')) {
                Map<String, Object> out = new LinkedHashMap<>();
                if (take('}')) return out;
                do {
                    space();
                    String key = string();
                    if (out.containsKey(key) || !take(':')) throw invalid();
                    out.put(key, value(depth + 1));
                    if (take('}')) return out;
                } while (take(','));
                throw invalid();
            }
            if (take('[')) {
                List<Object> out = new ArrayList<>();
                if (take(']')) return out;
                do {
                    out.add(value(depth + 1));
                    if (take(']')) return out;
                } while (take(','));
                throw invalid();
            }
            for (String literal : List.of("true", "false", "null")) {
                if (text.startsWith(literal, pos)) {
                    pos += literal.length();
                    return literal.equals("null") ? null : Boolean.valueOf(literal);
                }
            }
            int start = pos;
            while (pos < text.length() && "-+0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
            String number = text.substring(start, pos);
            if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw invalid();
            try { return new java.math.BigDecimal(number); }
            catch (NumberFormatException e) { throw invalid(); }
        }
        String string() {
            if (pos == text.length() || text.charAt(pos++) != '"') throw invalid();
            StringBuilder out = new StringBuilder();
            while (pos < text.length()) {
                char c = text.charAt(pos++);
                if (c == '"') return out.toString();
                if (c < 32) throw invalid();
                if (c != '\\') { out.append(c); continue; }
                if (pos == text.length()) throw invalid();
                char escape = text.charAt(pos++);
                switch (escape) {
                    case '"', '\\', '/' -> out.append(escape);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) throw invalid();
                        try { out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16)); }
                        catch (NumberFormatException e) { throw invalid(); }
                        pos += 4;
                    }
                    default -> throw invalid();
                }
            }
            throw invalid();
        }
    }

    public static int integer(Object value, int minimum, int maximum) {
        if (!(value instanceof Number)) throw invalid();
        try {
            int integer = new java.math.BigDecimal(value.toString()).intValueExact();
            if (integer < minimum || integer > maximum) throw invalid();
            return integer;
        } catch (ArithmeticException | NumberFormatException e) { throw invalid(); }
    }

}
