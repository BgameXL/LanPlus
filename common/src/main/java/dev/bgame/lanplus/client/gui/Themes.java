package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.Config;
import net.minecraft.network.chat.Component;

import java.util.List;

final class Themes {

    static final Theme AMETHYST = new Theme("amethyst", Component.translatable("gui.lanplus.theme.amethyst"),
            0xF21E1926, 0xFF18141F, 0xFF2C2538, 0xFF1D1824, 0xFF131018,
            0xFF443856, 0xFF0C0A0F,
            0xFF7B3FC4, 0xFF6A34AC, 0xFF9457DE, 0x407B3FC4, 0xFF7B3FC4, 0xFFB39AF0,
            0xFF57C07A, 0xFFD8A43C, 0xFFD05656,
            0xFFECEEF2, 0xFF8B909A, 0xFF6A6F78,
            0xFF2F273B, 0x14FFFFFF);

    static final Theme PERIWINKLE = new Theme("periwinkle", Component.translatable("gui.lanplus.theme.periwinkle"),
            0xF2181C2C, 0xFF131724, 0xFF242941, 0xFF171B2A, 0xFF0F121C,
            0xFF363F63, 0xFF090B11,
            0xFF7B8CFF, 0xFF6A7AE0, 0xFF92A0F2, 0x407B8CFF, 0xFF7B8CFF, 0xFF9AA6FF,
            0xFF57C07A, 0xFFD8A43C, 0xFFD05656,
            0xFFECEEF2, 0xFF8B909A, 0xFF6A6F78,
            0xFF252C45, 0x14FFFFFF);

    static final Theme EMBER = new Theme("ember", Component.translatable("gui.lanplus.theme.ember"),
            0xF2201A15, 0xFF1B1611, 0xFF2E2820, 0xFF1F1A14, 0xFF14100B,
            0xFF4A4238, 0xFF0C0906,
            0xFFE08A3C, 0xFFC0722A, 0xFFF0A24E, 0x40E08A3C, 0xFFE08A3C, 0xFFF0B87A,
            0xFF7FB03C, 0xFFE0A93C, 0xFFC7442E,
            0xFFF3EEE6, 0xFFC9BFAF, 0xFF9A8F7D,
            0xFF33302A, 0x14FFFFFF);

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

    static Theme resolve(String id) {
        if ("custom".equals(id)) {
            return custom(Config.customAccent, Config.customBackground, Config.customText);
        }
        return byId(id);
    }

    static Theme custom(int accent, int background, int text) {
        int ac = accent & 0xFFFFFF;
        int bg = background & 0xFFFFFF;
        int tx = text & 0xFFFFFF;
        return new Theme("custom", Component.translatable("gui.lanplus.theme.custom"),
                0xF2000000 | bg, shade(bg, 0.81f), shade(bg, 1.48f), shade(bg, 0.96f), shade(bg, 0.63f),
                shade(bg, 2.25f), shade(bg, 0.39f),
                0xFF000000 | ac, shade(ac, 0.86f), shade(ac, 1.20f), 0x40000000 | ac, 0xFF000000 | ac,
                0xFF000000 | mix(ac),
                AMETHYST.online(), AMETHYST.amber(), AMETHYST.red(),
                0xFF000000 | tx, shade(tx, 0.62f), shade(tx, 0.46f),
                shade(bg, 1.56f), 0x14FFFFFF);
    }

    private static int shade(int rgb, float f) {
        return LanPlusUI.shade(0xFF000000 | rgb, f);
    }

    private static int mix(int rgb) {
        int r = Math.round((rgb >> 16 & 0xFF) * (1 - (float) 0.45) + (16777215 >> 16 & 0xFF) * (float) 0.45);
        int g = Math.round((rgb >> 8 & 0xFF) * (1 - (float) 0.45) + (16777215 >> 8 & 0xFF) * (float) 0.45);
        int b = Math.round((rgb & 0xFF) * (1 - (float) 0.45) + (16777215 & 0xFF) * (float) 0.45);
        return (r << 16) | (g << 8) | b;
    }

    private Themes() {
    }
}
