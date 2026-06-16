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
            .define("backendUrl", "");

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
            .comment("Whether 'relayDevAddress' connects without TLS. Set true only for a local relay",
                    "started with LANPLUS_RELAY_TLS=false; leave false for a real TLS relay.")
            .define("relayDevPlaintext", false);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean enabled;
    public static String backendUrl = "";
    public static int heartbeatSeconds = 15;
    public static boolean relayEnabled = true;
    public static String relayDevAddress = "";
    public static boolean relayDevPlaintext = false;

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
    }
}
