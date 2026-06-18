package dev.bgame.lanplus.discord;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.api.PresenceSnapshot;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@link DiscordPresence} backed by Discord's local IPC (see {@link DiscordIpc}). Maps the local
 * {@link PresenceSnapshot} to a Rich Presence activity ("Hosting a world", world name, elapsed time).
 *
 * All IPC work runs on one daemon thread, so {@link #update} can be called from any thread (it is
 * wired as a presence listener). Fail-soft: if Discord is not running it stays silent
 * and retries on a throttle; nothing here ever throws to the caller. Disabled when {@code appId} is
 * blank, so it is inert unless the user configures a Discord application id.
 */
public final class DiscordRichPresence implements DiscordPresence {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long RETRY_MS = 30_000;
    private static final int MAX_TEXT = 128;

    private final String appId;
    private final boolean enabled;
    private final long pid = ProcessHandle.current().pid();
    private final long sessionStart = Instant.now().getEpochSecond();
    private final ExecutorService exec;

    private DiscordIpc ipc;
    private volatile boolean connected;
    private long lastAttempt;
    private volatile PresenceSnapshot last;

    public DiscordRichPresence(String appId) {
        this.appId = appId == null ? "" : appId.trim();
        this.enabled = !this.appId.isBlank();
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "lanplus-discord");
            t.setDaemon(true);
            return t;
        });
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
        ipc.send(DiscordIpc.OP_HANDSHAKE, hello.toString().getBytes(StandardCharsets.UTF_8));
        readResponse();
    }

    private void trySend(PresenceSnapshot snapshot) {
        try {
            ipc.send(DiscordIpc.OP_FRAME, activityFrame(snapshot).getBytes(StandardCharsets.UTF_8));
            readResponse();
        } catch (IOException e) {
            LOGGER.debug("LAN+ Discord push failed, dropping connection: {}", e.toString());
            connected = false;
            closeQuietly();
        }
    }

    private void readResponse() throws IOException {
        DiscordIpc.Frame frame = ipc.read();
        if (frame.opcode() == DiscordIpc.OP_PING) {
            ipc.send(DiscordIpc.OP_PONG, frame.data());
            frame = ipc.read();
        }
        if (frame.opcode() == DiscordIpc.OP_CLOSE) {
            throw new IOException("discord closed the connection");
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
        String details;
        String state = trim(snapshot.worldName());
        switch (snapshot.state()) {
            case SINGLEPLAYER -> details = "Exploring a world";
            case HOSTING -> details = "Hosting a world";
            case MULTIPLAYER -> details = "Playing online";
            default -> {
                details = "In the menus";
                state = null;
            }
        }

        JsonObject activity = new JsonObject();
        activity.addProperty("details", details);
        if (state != null) {
            activity.addProperty("state", state);
        }

        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", sessionStart);
        activity.add("timestamps", timestamps);

        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", "logo");
        assets.addProperty("large_text", "LAN+");
        activity.add("assets", assets);
        return activity;
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