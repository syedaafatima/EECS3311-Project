package ca.yorku.careeragent.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JsonSupport {
    private JsonSupport() {}

    static String quote(String value) {
        if (value == null) return "null";
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    static String stringField(String json, String key) {
        Pattern field = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*");
        Matcher match = field.matcher(json);
        if (!match.find()) return null;
        int start = match.end();
        if (json.startsWith("null", start)) return null;
        if (start >= json.length() || json.charAt(start) != '"') {
            throw new IllegalArgumentException("Expected a JSON string for " + key + ".");
        }

        // Scan iteratively: a repeated regex group over a full resume can overflow the stack.
        boolean escaped = false;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return unescape(json.substring(start + 1, i));
            }
        }
        throw new IllegalArgumentException("Unterminated JSON string for " + key + ".");
    }

    static List<String> stringArrayField(String json, String key) {
        Pattern p = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
        Matcher m = p.matcher(json);
        List<String> values = new ArrayList<>();
        if (!m.find()) return values;
        Matcher item = Pattern.compile("\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(m.group(1));
        while (item.find()) values.add(unescape(item.group(1)));
        return values;
    }

    static String arrayOfStrings(Iterable<String> values) {
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (String value : values) {
            if (!first) out.append(", ");
            out.append(quote(value));
            first = false;
        }
        return out.append(']').toString();
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'u' -> {
                        if (i + 4 >= value.length()) throw new IllegalArgumentException("Invalid JSON escape.");
                        out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '\\' -> out.append('\\');
                    case '"' -> out.append('"');
                    default -> out.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                out.append(c);
            }
        }
        if (escaped) out.append('\\');
        return out.toString();
    }
}