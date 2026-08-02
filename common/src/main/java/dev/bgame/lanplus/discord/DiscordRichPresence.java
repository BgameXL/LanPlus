package dev.bgame.lanplus.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.PresenceSnapshot;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * {@link DiscordPresence} backed by Discord's local IPC (see {@link DiscordIpc}). Maps the local
 * {@link PresenceSnapshot} to a Rich Presence activity ("Hosting Test Modpack", world name, party
 * size, per-world elapsed time) and, while hosting, carries the LAN+ invite code as the activity
 * join secret so friends can join straight from Discord.
 */
public final class DiscordRichPresence implements DiscordPresence {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long RETRY_MS = 30_000;
    private static final long REFRESH_SECONDS = 60;
    private static final int MAX_TEXT = 128;

    private final String appId;
    private final boolean enabled;
    private final long pid = ProcessHandle.current().pid();
    private final long sessionStart = Instant.now().getEpochSecond();
    private final ScheduledExecutorService exec;
    private final Supplier<int[]> partySize;
    private final Consumer<String> joinHandler;

    private final Object writeLock = new Object();
    private DiscordIpc ipc;
    private volatile boolean connected;
    private long lastAttempt;
    private volatile PresenceSnapshot last;
    private volatile String worldKey;
    private volatile long worldStart = sessionStart;

    public DiscordRichPresence(String appId, Supplier<int[]> partySize, Consumer<String> joinHandler) {
        this.appId = appId == null ? "" : appId.trim();
        this.enabled = !this.appId.isBlank();
        this.partySize = partySize;
        this.joinHandler = joinHandler;
        this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lanplus-discord");
            t.setDaemon(true);
            return t;
        });
        if (enabled) {
            exec.scheduleWithFixedDelay(this::push, REFRESH_SECONDS, REFRESH_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Override
    public boolean isAvailable() {
        return connected;
    }

    @Override
    public void update(PresenceSnapshot snapshot) {
        if (!enabled || snapshot == null) {
            return;
        }
        trackWorld(snapshot);
        last = snapshot;
        exec.execute(this::push);
    }

    @Override
    public void clear() {
        if (!enabled) {
            return;
        }
        last = null;
        exec.execute(() -> {
            if (connected) {
                trySend(null);
            }
        });
    }

    private void trackWorld(PresenceSnapshot snapshot) {
        String key = switch (snapshot.state()) {
            case SINGLEPLAYER, HOSTING, MULTIPLAYER -> snapshot.state().name() + "|" + snapshot.worldName();
            default -> null;
        };
        if (key != null && !key.equals(worldKey)) {
            worldStart = Instant.now().getEpochSecond();
        }
        worldKey = key;
    }

    private void push() {
        PresenceSnapshot snapshot = last;
        if (snapshot == null || !ensureConnected()) {
            return;
        }
        trySend(snapshot);
    }

    private boolean ensureConnected() {
        if (connected) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastAttempt < RETRY_MS) {
            return false;
        }
        lastAttempt = now;
        try {
            ipc = DiscordIpc.open();
            handshake();
            connected = true;
            subscribe("ACTIVITY_JOIN");
            subscribe("ACTIVITY_JOIN_REQUEST");
            Thread reader = new Thread(this::readLoop, "lanplus-discord-read");
            reader.setDaemon(true);
            reader.start();
            LOGGER.info("LAN+ Discord rich presence connected");
            return true;
        } catch (IOException e) {
            LOGGER.debug("LAN+ Discord IPC unavailable: {}", e.toString());
            closeQuietly();
            return false;
        }
    }

    private void handshake() throws IOException {
        JsonObject hello = new JsonObject();
        hello.addProperty("v", 1);
        hello.addProperty("client_id", appId);
        sendFrame(DiscordIpc.OP_HANDSHAKE, hello.toString());
        DiscordIpc.Frame ready = ipc.read();
        if (ready.opcode() == DiscordIpc.OP_CLOSE) {
            throw new IOException("discord rejected the handshake");
        }
    }

    private void subscribe(String event) {
        JsonObject payload = new JsonObject();
        payload.addProperty("cmd", "SUBSCRIBE");
        payload.addProperty("evt", event);
        payload.addProperty("nonce", UUID.randomUUID().toString());
        try {
            sendFrame(DiscordIpc.OP_FRAME, payload.toString());
        } catch (IOException e) {
            LOGGER.debug("LAN+ Discord subscribe {} failed: {}", event, e.toString());
        }
    }

    private void readLoop() {
        DiscordIpc mine = ipc;
        try {
            while (connected && mine == ipc) {
                DiscordIpc.Frame frame = mine.read();
                switch (frame.opcode()) {
                    case DiscordIpc.OP_PING -> sendFrame(DiscordIpc.OP_PONG, new String(frame.data(), StandardCharsets.UTF_8));
                    case DiscordIpc.OP_CLOSE -> throw new IOException("discord closed the connection");
                    case DiscordIpc.OP_FRAME -> handleEvent(new String(frame.data(), StandardCharsets.UTF_8));
                    default -> { }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("LAN+ Discord connection lost: {}", e.toString());
            connected = false;
            closeQuietly();
        }
    }

    private void handleEvent(String json) {
        JsonObject payload;
        try {
            payload = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        String evt = payload.has("evt") && !payload.get("evt").isJsonNull()
                ? payload.get("evt").getAsString() : null;
        if (evt == null || !payload.has("data") || payload.get("data").isJsonNull()) {
            return;
        }
        JsonObject data = payload.getAsJsonObject("data");
        switch (evt) {
            case "ERROR" -> LOGGER.warn("LAN+ Discord rejected a command: {}", data);
            case "ACTIVITY_JOIN" -> {
                String secret = data.has("secret") ? data.get("secret").getAsString() : null;
                if (secret != null && joinHandler != null) {
                    LOGGER.info("LAN+ joining a world from a Discord invite");
                    try {
                        joinHandler.accept(secret);
                    } catch (RuntimeException e) {
                        LOGGER.warn("LAN+ Discord join failed", e);
                    }
                }
            }
            case "ACTIVITY_JOIN_REQUEST" -> {
                JsonObject user = data.has("user") && data.get("user").isJsonObject()
                        ? data.getAsJsonObject("user") : null;
                String userId = user != null && user.has("id") ? user.get("id").getAsString() : null;
                if (userId != null) {
                    JsonObject reply = new JsonObject();
                    reply.addProperty("cmd", "SEND_ACTIVITY_JOIN_INVITE");
                    reply.addProperty("nonce", UUID.randomUUID().toString());
                    JsonObject args = new JsonObject();
                    args.addProperty("user_id", userId);
                    reply.add("args", args);
                    try {
                        sendFrame(DiscordIpc.OP_FRAME, reply.toString());
                    } catch (IOException e) {
                        LOGGER.debug("LAN+ Discord join-invite reply failed: {}", e.toString());
                    }
                }
            }
            default -> { }
        }
    }

    private void trySend(PresenceSnapshot snapshot) {
        try {
            sendFrame(DiscordIpc.OP_FRAME, activityFrame(snapshot));
        } catch (IOException e) {
            LOGGER.debug("LAN+ Discord push failed, dropping connection: {}", e.toString());
            connected = false;
            closeQuietly();
        }
    }

    private void sendFrame(int opcode, String json) throws IOException {
        DiscordIpc target = ipc;
        if (target == null) {
            throw new IOException("not connected");
        }
        synchronized (writeLock) {
            target.send(opcode, json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String activityFrame(PresenceSnapshot snapshot) {
        JsonObject payload = new JsonObject();
        payload.addProperty("cmd", "SET_ACTIVITY");
        payload.addProperty("nonce", UUID.randomUUID().toString());

        JsonObject args = new JsonObject();
        args.addProperty("pid", pid);
        args.add("activity", snapshot == null ? JsonNull.INSTANCE : activity(snapshot));
        payload.add("args", args);
        return payload.toString();
    }

    private JsonObject activity(PresenceSnapshot snapshot) {
        String pack = prettyModpack(snapshot.modpackId());
        String details;
        String state = trim(snapshot.worldName());
        boolean inWorld = true;
        switch (snapshot.state()) {
            case SINGLEPLAYER -> details = pack == null ? "Exploring a world" : "Exploring " + pack;
            case HOSTING -> details = pack == null ? "Hosting a world" : "Hosting " + pack;
            case MULTIPLAYER -> details = pack == null ? "Playing online" : "Playing " + pack;
            default -> {
                details = "In the menus";
                state = null;
                inWorld = false;
            }
        }

        JsonObject activity = new JsonObject();
        activity.addProperty("details", trim(details));
        if (state != null) {
            activity.addProperty("state", state);
        }

        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", inWorld ? worldStart : sessionStart);
        activity.add("timestamps", timestamps);

        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", "logo");
        assets.addProperty("large_text", largeText(snapshot));
        activity.add("assets", assets);

        boolean hosting = snapshot.state() == GameplayState.HOSTING;
        String joinCode = snapshot.joinCode();
        if (hosting && joinCode != null && !joinCode.isBlank()) {
            JsonObject party = new JsonObject();
            party.addProperty("id", "lanplus-" + joinCode);
            int[] size = partySize == null ? null : safePartySize();
            if (size != null && size.length == 2 && size[0] >= 1 && size[1] >= size[0]) {
                JsonArray sizes = new JsonArray();
                sizes.add(size[0]);
                sizes.add(size[1]);
                party.add("size", sizes);
            }
            activity.add("party", party);

            JsonObject secrets = new JsonObject();
            secrets.addProperty("join", joinCode);
            activity.add("secrets", secrets);
        }
        return activity;
    }

    private int[] safePartySize() {
        try {
            return partySize.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String largeText(PresenceSnapshot snapshot) {
        if (snapshot.state() != GameplayState.HOSTING || snapshot.accessMode() == null) {
            return "LAN+";
        }
        return switch (snapshot.accessMode()) {
            case FRIENDS -> "LAN+ - friends only";
            case INVITED -> "LAN+ - invite only";
            default -> "LAN+ - open world";
        };
    }

    private static String prettyModpack(String modpackId) {
        if (modpackId == null || modpackId.isBlank()) {
            return null;
        }
        String[] words = modpackId.trim().split("[-_\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.length() == 0 ? null : trim(sb.toString());
    }

    private static String trim(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.length() <= MAX_TEXT ? s : s.substring(0, MAX_TEXT);
    }

    private void closeQuietly() {
        if (ipc != null) {
            try {
                ipc.close();
            } catch (IOException ignored) {
            }
            ipc = null;
        }
    }
}