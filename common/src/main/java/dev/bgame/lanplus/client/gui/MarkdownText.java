package dev.bgame.lanplus.client.gui;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

final class MarkdownText {

    record Block(Component text, int indent, int gapAbove) {
    }

    private MarkdownText() {
    }

    static Component line(String s, int codeColor, int linkColor) {
        return s == null ? Component.empty() : inline(s, codeColor, linkColor);
    }

    static List<Block> parse(String body, int headingColor, int codeColor, int linkColor) {
        List<Block> out = new ArrayList<>();
        if (body == null) {
            return out;
        }
        for (String raw : body.split("\n", -1)) {
            if (raw.isBlank()) {
                out.add(new Block(Component.empty(), 0, 0));
            } else if (raw.startsWith("## ")) {
                out.add(new Block(inline(raw.substring(3), codeColor, linkColor)
                        .withStyle(Style.EMPTY.withColor(headingColor).withBold(true)), 0, 3));
            } else if (raw.startsWith("# ")) {
                out.add(new Block(inline(raw.substring(2), codeColor, linkColor)
                        .withStyle(Style.EMPTY.withColor(headingColor).withBold(true)), 0, 4));
            } else if (raw.startsWith("- ") || raw.startsWith("* ")) {
                out.add(new Block(Component.literal("• ")
                        .append(inline(raw.substring(2), codeColor, linkColor)), 6, 0));
            } else {
                out.add(new Block(inline(raw, codeColor, linkColor), 0, 0));
            }
        }
        return out;
    }

    private static MutableComponent inline(String s, int codeColor, int linkColor) {
        MutableComponent root = Component.empty();
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '[') {
                int close = s.indexOf(']', i + 1);
                if (close > 0 && close + 1 < s.length() && s.charAt(close + 1) == '(') {
                    int paren = s.indexOf(')', close + 2);
                    if (paren > 0) {
                        flush(root, plain);
                        String url = s.substring(close + 2, paren);
                        root.append(Component.literal(s.substring(i + 1, close)).withStyle(Style.EMPTY
                                .withColor(linkColor).withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal(url)))));
                        i = paren + 1;
                        continue;
                    }
                }
            }
            if (c == '`') {
                int close = s.indexOf('`', i + 1);
                if (close > 0) {
                    flush(root, plain);
                    root.append(Component.literal(s.substring(i + 1, close))
                            .withStyle(Style.EMPTY.withColor(codeColor)));
                    i = close + 1;
                    continue;
                }
            }
            if (c == '*' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                int close = s.indexOf("**", i + 2);
                if (close > 0) {
                    flush(root, plain);
                    root.append(inline(s.substring(i + 2, close), codeColor, linkColor)
                            .withStyle(Style.EMPTY.withBold(true)));
                    i = close + 2;
                    continue;
                }
            }
            if (c == '*' || c == '_') {
                int close = s.indexOf(c, i + 1);
                if (close > i + 1) {
                    flush(root, plain);
                    root.append(inline(s.substring(i + 1, close), codeColor, linkColor)
                            .withStyle(Style.EMPTY.withItalic(true)));
                    i = close + 1;
                    continue;
                }
            }
            plain.append(c);
            i++;
        }
        flush(root, plain);
        return root;
    }

    private static void flush(MutableComponent root, StringBuilder plain) {
        if (!plain.isEmpty()) {
            root.append(Component.literal(plain.toString()));
            plain.setLength(0);
        }
    }
}
