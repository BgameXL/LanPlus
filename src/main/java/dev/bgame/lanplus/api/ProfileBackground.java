package dev.bgame.lanplus.api;

public record ProfileBackground(String style, int color, int opacity) {

    public static final ProfileBackground DEFAULT = new ProfileBackground("DARK", 0x0A0C10, 92);

    public ProfileBackground {
        if (style == null) {
            style = DEFAULT.style;
        }
        color &= 0xFFFFFF;
        opacity = Math.max(0, Math.min(100, opacity));
    }
}
