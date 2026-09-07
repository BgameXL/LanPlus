package dev.bgame.lanplus.client.gui;

import net.minecraft.network.chat.Component;

record Theme(String id, Component name,
             int surface, int surfaceRaised, int surfaceHover, int surfaceDisabled, int slot,
             int edgeLight, int edgeDark,
             int accent, int accentStrong, int accentHover, int accentTint, int accentLine, int link,
             int online, int amber, int red,
             int text, int muted, int faint,
             int border, int divider) {
}
