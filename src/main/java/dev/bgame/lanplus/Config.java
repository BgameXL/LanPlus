package dev.bgame.lanplus;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = Lanplus.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Master switch for LAN+ backend connectivity. When off, the mod runs in local-only mode.")
            .define("enabled", true);

    private static final ForgeConfigSpec.ConfigValue<String> BACKEND_URL = BUILDER
            .comment("Base HTTP(S) URL of the LAN+ backend (e.g. http://localhost:8080). Empty = local-only mode.")
            .define("backendUrl", "https://backend.lanplus.dev");

    private static final ForgeConfigSpec.IntValue HEARTBEAT_SECONDS = BUILDER
            .comment("How often to push a presence heartbeat to the backend, in seconds.")
            .defineInRange("heartbeatSeconds", 15, 5, 300);

    private static final ForgeConfigSpec.BooleanValue RELAY_ENABLED = BUILDER
            .comment("Expose hosted worlds over the internet through the LAN+ relay (no port forwarding).",
                    "When off, hosting is LAN-only.")
            .define("relayEnabled", true);

    private static final ForgeConfigSpec.ConfigValue<String> RELAY_DEV_ADDRESS = BUILDER
            .comment("Manual relay host:port that bypasses the backend ticket (relay must run with",
                    "LANPLUS_RELAY_NO_AUTH=true). Use to test a relay before the backend exists, e.g.",
                    "relay.lanplus.dev:8443. Empty = normal mode (ticket from the backend).")
            .define("relayDevAddress", "");

    private static final ForgeConfigSpec.BooleanValue RELAY_DEV_PLAINTEXT = BUILDER
            .comment("Connect to the relay without TLS. Applies to both the backend-ticket path and",
                    "'relayDevAddress', so a fully local backend+relay can be tested without certs.",
                    "Set true only for a local relay started with LANPLUS_RELAY_TLS=false;",
                    "leave false for a real TLS relay.")
            .define("relayDevPlaintext", false);

    private static final ForgeConfigSpec.ConfigValue<String> SKIN_URL = BUILDER
            .comment("Custom skin: a direct https URL to your skin PNG, shown to friends. Use this if",
                    "you are non-premium (offline) so you still have a skin. Empty = use your Mojang",
                    "skin (premium). Only https URLs to public hosts are fetched.")
            .define("skinUrl", "");

    private static final ForgeConfigSpec.BooleanValue SKIN_SLIM = BUILDER
            .comment("Arm model for the custom 'skinUrl' skin: true = slim (Alex), false = classic.",
                    "Ignored when skinUrl is empty (Mojang reports its own model).")
            .define("skinSlim", false);

    private static final ForgeConfigSpec.BooleanValue DISCORD_ENABLED = BUILDER
            .comment("Show your LAN+ status in Discord via Rich Presence. Needs 'discordAppId' set and",
                    "the Discord desktop app running; otherwise this does nothing.")
            .define("discordEnabled", true);

    private static final ForgeConfigSpec.ConfigValue<String> DISCORD_APP_ID = BUILDER
            .comment("Discord application id (snowflake) used for Rich Presence. Create a free app at",
                    "https://discord.com/developers/applications to get one; its name + 'logo' asset are",
                    "what Discord shows. Empty = Rich Presence off.")
            .define("discordAppId", "1516914761626030170");

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean enabled;
    public static String backendUrl = "";
    public static int heartbeatSeconds = 15;
    public static boolean relayEnabled = true;
    public static String relayDevAddress = "";
    public static boolean relayDevPlaintext = false;
    public static String skinUrl = "";
    public static boolean skinSlim = false;
    public static boolean discordEnabled = true;
    public static String discordAppId = "";

    private Config() {}

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        enabled = ENABLED.get();
        backendUrl = BACKEND_URL.get();
        heartbeatSeconds = HEARTBEAT_SECONDS.get();
        relayEnabled = RELAY_ENABLED.get();
        relayDevAddress = RELAY_DEV_ADDRESS.get();
        relayDevPlaintext = RELAY_DEV_PLAINTEXT.get();
        skinUrl = SKIN_URL.get();
        skinSlim = SKIN_SLIM.get();
        discordEnabled = DISCORD_ENABLED.get();
        discordAppId = DISCORD_APP_ID.get();
    }
}
