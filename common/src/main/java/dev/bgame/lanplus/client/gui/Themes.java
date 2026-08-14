package dev.bgame.lanplus.client.gui;

import net.minecraft.network.chat.Component;

import java.util.List;

final class Themes {

    static final Theme AMETHYST = new Theme("amethyst", Component.translatable("gui.lanplus.theme.amethyst"),
            0xF21A1C22, 0xFF15171C, 0xFF262A33, 0xFF191B21, 0xFF101216,
            0xFF3A3F4E, 0xFF0A0B0E,
            0xFF7B3FC4, 0xFF6A34AC, 0xFF9457DE, 0x407B3FC4, 0xFF7B3FC4, 0xFFB39AF0,
            0xFF57C07A, 0xFFD8A43C, 0xFFD05656,
            0xFFECEEF2, 0xFF8B909A, 0xFF6A6F78,
            0xFF2A2C33, 0x14FFFFFF, 0xC00A0B0D);

    static final Theme PERIWINKLE = new Theme("periwinkle", Component.translatable("gui.lanplus.theme.periwinkle"),
            0xF21A1C22, 0xFF15171C, 0xFF262A33, 0xFF191B21, 0xFF101216,
            0xFF3A3F4E, 0xFF0A0B0E,
            0xFF7B8CFF, 0xFF6A7AE0, 0xFF92A0F2, 0x407B8CFF, 0xFF7B8CFF, 0xFF9AA6FF,
            0xFF57C07A, 0xFFD8A43C, 0xFFD05656,
            0xFFECEEF2, 0xFF8B909A, 0xFF6A6F78,
            0xFF2A2C33, 0x14FFFFFF, 0xC00A0B0D);

    static final Theme EMBER = new Theme("ember", Component.translatable("gui.lanplus.theme.ember"),
            0xF2201A15, 0xFF1B1611, 0xFF2E2820, 0xFF1F1A14, 0xFF14100B,
            0xFF4A4238, 0xFF0C0906,
            0xFFE08A3C, 0xFFC0722A, 0xFFF0A24E, 0x40E08A3C, 0xFFE08A3C, 0xFFF0B87A,
            0xFF7FB03C, 0xFFE0A93C, 0xFFC7442E,
            0xFFF3EEE6, 0xFFC9BFAF, 0xFF9A8F7D,
            0xFF33302A, 0x14FFFFFF, 0xC00D0A06);

    static final List<Theme> ALL = List.of(AMETHYST, PERIWINKLE, EMBER);
    static final Theme DEFAULT = AMETHYST;

    static Theme byId(String id) {
        for (Theme t : ALL) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return DEFAULT;
    }

    private Themes() {
    }
}
