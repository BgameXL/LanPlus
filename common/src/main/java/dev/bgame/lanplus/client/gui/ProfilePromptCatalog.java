package dev.bgame.lanplus.client.gui;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The fixed catalog of "Questions about yourself" prompts. Predefined by LAN+ (the player never invents a
 * question, only answers one of these). The backend keeps a mirror whitelist of just the IDs (Store.PROMPT_IDS);
 * the prompt text and choices live here, client-side, localized via lang.
 */
public final class ProfilePromptCatalog {

    public enum Type { FREE, CHOICE }

    public record Prompt(String id, Type type, List<String> choices) {
        public Component question() {
            return Component.translatable("gui.lanplus.profile.prompt." + id);
        }
    }

    public static final List<Prompt> PROMPTS = List.of(
            new Prompt("delete_block", Type.FREE, List.of()),
            new Prompt("first_night", Type.FREE, List.of()),
            new Prompt("build_first", Type.FREE, List.of()),
            new Prompt("useless_item", Type.FREE, List.of()),
            new Prompt("difficulty", Type.CHOICE, List.of("peaceful", "easy", "normal", "hard", "hardcore")),
            new Prompt("travel", Type.CHOICE, List.of("boats", "horses", "elytra", "nether_highway", "minecart", "walking")),
            new Prompt("armor", Type.CHOICE, List.of("diamond", "netherite", "iron", "none")),
            new Prompt("playstyle", Type.CHOICE, List.of("builder", "redstoner", "explorer", "farmer", "fighter", "hoarder")));

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

    private ProfilePromptCatalog() {
    }
}
