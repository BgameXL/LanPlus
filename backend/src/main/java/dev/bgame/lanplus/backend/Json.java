package dev.bgame.lanplus.backend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {

    private Json() {}

    // write
    static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String s) {
            writeString(sb, s);
        } else if (o instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (o instanceof Integer || o instanceof Long) {
            sb.append(o);
        } else if (o instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                sb.append(Long.toString((long) d));
            } else {
                sb.append(n);
            }
        } else if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getValue() == null) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof List<?> l) {
            sb.append('[');
            boolean first = true;
            for (Object e : l) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(sb, e);
            }
            sb.append(']');
        } else {
            writeString(sb, o.toString());
        }
    }

    private static void writeString(StringBuilder sb, String v) {
        sb.append('"');
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
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

    // parse
    static Object parse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            int[] pos = {0};
            skipWs(s, pos);
            Object v = parseValue(s, pos);
            return v;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Convenience: parse and cast to a string-keyed map (empty map if not an object). */
    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(String s) {
        Object v = parse(s);
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
    }

    private static Object parseValue(String s, int[] pos) {
        skipWs(s, pos);
        char c = s.charAt(pos[0]);
        return switch (c) {
            case '{' -> parseObject(s, pos);
            case '[' -> parseArray(s, pos);
            case '"' -> parseString(s, pos);
            case 't', 'f' -> parseBool(s, pos);
            case 'n' -> parseNull(s, pos);
            default -> parseNumber(s, pos);
        };
    }

    private static Map<String, Object> parseObject(String s, int[] pos) {
        Map<String, Object> map = new LinkedHashMap<>();
        pos[0]++; // {
        skipWs(s, pos);
        if (s.charAt(pos[0]) == '}') {
            pos[0]++;
            return map;
        }
        while (true) {
            skipWs(s, pos);
            String key = parseString(s, pos);
            skipWs(s, pos);
            pos[0]++; // :
            Object value = parseValue(s, pos);
            map.put(key, value);
            skipWs(s, pos);
            char c = s.charAt(pos[0]++);
            if (c == '}') {
                break;
            }
        }
        return map;
    }

    private static List<Object> parseArray(String s, int[] pos) {
        List<Object> list = new ArrayList<>();
        pos[0]++; // [
        skipWs(s, pos);
        if (s.charAt(pos[0]) == ']') {
            pos[0]++;
            return list;
        }
        while (true) {
            list.add(parseValue(s, pos));
            skipWs(s, pos);
            char c = s.charAt(pos[0]++);
            if (c == ']') {
                break;
            }
        }
        return list;
    }

    private static String parseString(String s, int[] pos) {
        StringBuilder sb = new StringBuilder();
        pos[0]++; // opening quote
        while (true) {
            char c = s.charAt(pos[0]++);
            if (c == '"') {
                break;
            }
            if (c == '\\') {
                char e = s.charAt(pos[0]++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(s.substring(pos[0], pos[0] + 4), 16));
                        pos[0] += 4;
                    }
                    default -> sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Boolean parseBool(String s, int[] pos) {
        if (s.startsWith("true", pos[0])) {
            pos[0] += 4;
            return Boolean.TRUE;
        }
        pos[0] += 5;
        return Boolean.FALSE;
    }

    private static Object parseNull(String s, int[] pos) {
        pos[0] += 4; // null
        return null;
    }

    private static Double parseNumber(String s, int[] pos) {
        int start = pos[0];
        while (pos[0] < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos[0])) >= 0) {
            pos[0]++;
        }
        return Double.parseDouble(s.substring(start, pos[0]));
    }

    private static void skipWs(String s, int[] pos) {
        while (pos[0] < s.length() && Character.isWhitespace(s.charAt(pos[0]))) {
            pos[0]++;
        }
    }
}
