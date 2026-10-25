package ca.yorku.careeragent.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JsonSupport {
    private JsonSupport() {}

    static String quote(String value) {
        if (value == null) return "null";
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    static String stringField(String json, String key) {
        Pattern p = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*(null|\\\"((?:\\\\.|[^\\\"\\\\])*)\\\")");
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        if ("null".equals(m.group(1))) return null;
        return unescape(m.group(2));
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