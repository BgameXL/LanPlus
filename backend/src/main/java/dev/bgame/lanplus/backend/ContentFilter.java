package dev.bgame.lanplus.backend;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Hard-block filter for hate speech in free-text profile fields.
 * Scope is slurs only, not general profanity, and what slips through
 * is caught by user reports + admin.
*/
final class ContentFilter {

    private ContentFilter() {}

    private static final List<String> ROOTS = List.of(
            "nigger", "nigga", "niglet", "jigaboo", "spearchucker", "porchmonkey",
            "faggot", "faggy", "fudgepacker",
            "kike", "wetback", "beaner", "raghead", "towelhead", "sandnigger",
            "shitskin", "junglebunny", "tranny", "shemale", "zipperhead", "gook"
    );

    private static final Pattern[] PATTERNS = ROOTS.stream()
            .map(ContentFilter::toRepeatPattern)
            .toArray(Pattern[]::new);

    private static final Pattern COMBINING = Pattern.compile("\\p{M}+");

    static boolean isBlocked(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String norm = normalize(text);
        if (norm.isEmpty()) {
            return false;
        }
        for (Pattern p : PATTERNS) {
            if (p.matcher(norm).find()) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        String base = Normalizer.normalize(text, Normalizer.Form.NFKD);
        base = COMBINING.matcher(base).replaceAll("");
        base = base.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char c = fold(base.charAt(i));
            if (c >= 'a' && c <= 'z') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static char fold(char c) {
        return switch (c) {
            case '0' -> 'o';
            case '1', '!', '|' -> 'i';
            case '3' -> 'e';
            case '4', '@' -> 'a';
            case '5', '$' -> 's';
            case '7' -> 't';
            case '8' -> 'b';
            case '9' -> 'g';
            case '(' -> 'c';
            case '+' -> 't';
            default -> c;
        };
    }

   // a nice regex
    private static Pattern toRepeatPattern(String root) {
        StringBuilder sb = new StringBuilder(root.length() * 2);
        for (int i = 0; i < root.length(); i++) {
            sb.append(root.charAt(i)).append('+');
        }
        return Pattern.compile(sb.toString());
    }
}