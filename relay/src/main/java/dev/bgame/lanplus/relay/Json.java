package dev.bgame.lanplus.relay;

import java.util.LinkedHashMap;
import java.util.Map;

final class Json {

    private Json() {}

    static String obj(String... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            quote(sb, kv[i]).append(':');
            quote(sb, kv[i + 1]);
        }
        return sb.append('}').toString();
    }

    static Map<String, String> parse(String s) {
        Map<String, String> out = new LinkedHashMap<>();
        if (s == null) {
            return out;
        }
        int n = s.length();
        int i = skipWs(s, 0);
        if (i >= n || s.charAt(i) != '{') {
            return out;
        }
        i++;
        while (true) {
            i = skipWs(s, i);
            if (i >= n || s.charAt(i) != '"') {
                break; // '}' or malformed
            }
            StringBuilder key = new StringBuilder();
            i = readString(s, i, key);
            i = skipWs(s, i);
            if (i >= n || s.charAt(i) != ':') {
                break;
            }
            i = skipWs(s, i + 1);
            if (i >= n) {
                break;
            }
            if (s.charAt(i) == '"') {
                StringBuilder val = new StringBuilder();
                i = readString(s, i, val);
                out.put(key.toString(), val.toString());
            } else {
                int start = i;
                while (i < n && ",}".indexOf(s.charAt(i)) < 0) {
                    i++;
                }
                out.put(key.toString(), s.substring(start, i).trim());
            }
            i = skipWs(s, i);
            if (i < n && s.charAt(i) == ',') {
                i++;
                continue;
            }
            break;
        }
        return out;
    }

    private static int skipWs(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int readString(String s, int i, StringBuilder out) {
        i++; // opening quote
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i++);
            if (c == '"') {
                break;
            }
            if (c == '\\' && i < n) {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> out.append(e); // " \ /
                }
            } else {
                out.append(c);
            }
        }
        return i;
    }

    private static StringBuilder quote(StringBuilder sb, String v) {
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
        return sb.append('"');
    }
}
