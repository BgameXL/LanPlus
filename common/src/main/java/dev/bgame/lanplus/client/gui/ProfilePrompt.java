package dev.bgame.lanplus.client.gui;

import net.minecraft.network.chat.Component;

import java.util.List;

public final class ProfilePrompt {

    public enum Type {FREE, CHOICE}

    public record Prompt(String id, Type type, List<String> choices) {
        public Component question() {
            return Component.translatable("gui.lanplus.profile.prompt." + id);
        }
    }

    public static final List<Prompt> PROMPTS = List.of(
            new Prompt("dumb_death", Type.FREE, List.of()),
            new Prompt("join_world", Type.FREE, List.of()),
            new Prompt("armor", Type.CHOICE, List.of("diamond", "netherite", "iron", "leather", "none")),
            new Prompt("vanilla_mod", Type.FREE, List.of()),
            new Prompt("fav_version", Type.FREE, List.of()),
            new Prompt("solo_or_friends", Type.CHOICE, List.of("solo", "friends", "depends")),
            new Prompt("gamemode", Type.CHOICE, List.of("survival", "creative", "hardcore")),
            new Prompt("too_many_mods", Type.FREE, List.of()),
            new Prompt("three_mods", Type.FREE, List.of()),
            new Prompt("unused_mechanic", Type.FREE, List.of()));

    public static Prompt byId(String id) {
        if (id == null) {
            return null;
        }
        for (Prompt p : PROMPTS) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

    public static Component choiceLabel(Prompt p, String token) {
        return Component.translatable("gui.lanplus.profile.prompt." + p.id() + ".choice." + token);
    }

    public static Component answerText(Prompt p, String answer) {
        if (answer == null) {
            return Component.empty();
        }
        if (p != null && p.type() == Type.CHOICE && p.choices().contains(answer)) {
            return choiceLabel(p, answer);
        }
        return Component.literal(answer);
    }

    private ProfilePrompt() {
    }
}
