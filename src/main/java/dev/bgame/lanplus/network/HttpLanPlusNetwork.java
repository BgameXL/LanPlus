package dev.bgame.lanplus.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.api.Connectivity;
import dev.bgame.lanplus.api.Friend;
import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.Invite;
import dev.bgame.lanplus.api.PlayerIdentity;
import dev.bgame.lanplus.api.PresenceSnapshot;
import dev.bgame.lanplus.api.PresenceUpdate;
import dev.bgame.lanplus.api.RelayTicket;
import dev.bgame.lanplus.api.ResolvedUser;
import dev.bgame.lanplus.api.UserProfile;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class HttpLanPlusNetwork implements LanPlusNetwork {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long RECONNECT_BASE_MS = 2_000L;
    private static final long RECONNECT_MAX_MS = 60_000L;

    private final Supplier<String> baseUrl;
    private final Supplier<PlayerIdentity> identity;
    private final HttpClient http;
    private final ScheduledExecutorService scheduler;

    private volatile boolean reachable = false;
    private volatile WebSocket webSocket;

    // Realtime channel state. eventsEnabled gates reconnection so an intentional disconnect() stays disconnected.
    private volatile UUID eventsUuid;
    private volatile BackendEventListener eventsListener;
    private volatile boolean eventsEnabled = false;
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final AtomicBoolean reconnectPending = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    public HttpLanPlusNetwork(Supplier<String> baseUrl, Supplier<PlayerIdentity> identity) {
        this.baseUrl = baseUrl;
        this.identity = identity;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "lanplus-ws-reconnect");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public CompletableFuture<Void> pushPresence(PresenceSnapshot snapshot) {
        PlayerIdentity id = identity.get();
        if (!configured() || id == null) {
            return CompletableFuture.completedFuture(null);
        }
        Wire.PresenceRequest body = new Wire.PresenceRequest(
                id.uuid().toString(),
                id.username(),
                snapshot.state().name(),
                snapshot.worldName(),
                snapshot.address(),
                snapshot.joinCode(),
                Wire.Skin.from(snapshot.skin()),
                System.currentTimeMillis());
        return post("/presence", body)
                .thenAccept(resp -> { })
                .exceptionally(this::onErrorVoid);
    }

    @Override
    public CompletableFuture<List<Friend>> getFriends(UUID uuid) {
        if (!configured()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return get("/friends/" + uuid)
                .thenApply(resp -> {
                    Wire.Friend[] arr = GSON.fromJson(resp.body(), Wire.Friend[].class);
                    if (arr == null) {
                        return List.<Friend>of();
                    }
                    List<Friend> out = new ArrayList<>(arr.length);
                    for (Wire.Friend f : arr) {
                        out.add(f.toApi());
                    }
                    return out;
                })
                .exceptionally(err -> {
                    onError(err);
                    return List.of();
                });
    }

    @Override
    public CompletableFuture<Boolean> addFriend(UUID uuid, UUID friendUuid) {
        if (!configured()) {
            return CompletableFuture.completedFuture(false);
        }
        return post("/friends/add", new Wire.FriendAdd(uuid.toString(), friendUuid.toString()))
                .thenApply(resp -> {
                    Wire.Success ok = GSON.fromJson(resp.body(), Wire.Success.class);
                    return ok != null && ok.success();
                })
                .exceptionally(err -> {
                    onError(err);
                    return false;
                });
    }

    @Override
    public CompletableFuture<ResolvedUser> resolveUser(String query) {
        if (!configured() || query == null || query.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        return get("/users/resolve?query=" + encoded)
                .thenApply(resp -> {
                    Wire.ResolvedUserDto user = GSON.fromJson(resp.body(), Wire.ResolvedUserDto.class);
                    return user == null || user.uuid() == null ? null : user.toApi();
                })
                .exceptionally(err -> {
                    onError(err);
                    return null;
                });
    }

    @Override
    public CompletableFuture<UserProfile> fetchProfile(UUID uuid) {
        if (!configured() || uuid == null) {
            return CompletableFuture.completedFuture(null);
        }
        return get("/users/me?uuid=" + uuid)
                .thenApply(resp -> {
                    Wire.UserProfileDto profile = GSON.fromJson(resp.body(), Wire.UserProfileDto.class);
                    return profile == null || profile.uuid() == null ? null : profile.toApi();
                })
                .exceptionally(err -> {
                    onError(err);
                    return null;
                });
    }

    @Override
    public CompletableFuture<Invite> createInvite(UUID hostUuid, String address, String worldName) {
        if (!configured()) {
            return CompletableFuture.completedFuture(null);
        }
        return post("/invite/create", new Wire.InviteCreate(hostUuid.toString(), address, worldName))
                .thenApply(resp -> {
                    Wire.InviteCreated c = GSON.fromJson(resp.body(), Wire.InviteCreated.class);
                    return c == null ? null : new Invite(c.code(), address, worldName, c.expiresIn());
                })
                .exceptionally(err -> {
                    onError(err);
                    return null;
                });
    }

    @Override
    public CompletableFuture<Invite> resolveInvite(String code) {
        if (!configured()) {
            return CompletableFuture.completedFuture(null);
        }
        return get("/invite/" + code)
                .thenApply(resp -> {
                    Wire.InviteResolved r = GSON.fromJson(resp.body(), Wire.InviteResolved.class);
                    return r == null ? null : new Invite(code, r.address(), r.worldName(), 0);
                })
                .exceptionally(err -> {
                    onError(err);
                    return null;
                });
    }

    @Override
    public CompletableFuture<RelayTicket> requestRelayTicket() {
        PlayerIdentity id = identity.get();
        if (!configured() || id == null) {
            return CompletableFuture.completedFuture(null);
        }
        return post("/relay/ticket", new Wire.RelayTicketRequest(id.uuid().toString()))
                .thenApply(resp -> {
                    Wire.RelayTicketDto dto = GSON.fromJson(resp.body(), Wire.RelayTicketDto.class);
                    return dto == null || dto.ticket() == null ? null : dto.toApi();
                })
                .exceptionally(err -> {
                    onError(err);
                    return null;
                });
    }

    @Override
    public void connectEvents(UUID uuid, BackendEventListener listener) {
        if (!configured()) {
            return;
        }
        this.eventsUuid = uuid;
        this.eventsListener = listener;
        this.eventsEnabled = true;
        this.reconnectAttempts.set(0);
        openSocket();
    }

    @Override
    public void disconnect() {
        this.eventsEnabled = false;
        WebSocket ws = this.webSocket;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "client shutdown");
        }
        this.webSocket = null;
    }

    @Override
    public boolean isConnected() {
        return reachable;
    }

    // internals
    private boolean configured() {
        return !base().isEmpty();
    }

    private String base() {
        String url = baseUrl.get();
        if (url == null) {
            return "";
        }
        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private CompletableFuture<HttpResponse<String>> get(String path) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        return send(req);
    }

    private CompletableFuture<HttpResponse<String>> post(String path, Object body) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();
        return send(req);
    }

    private CompletableFuture<HttpResponse<String>> send(HttpRequest req) {
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .whenComplete((resp, err) -> reachable = err == null && resp != null);
    }

    private void onError(Throwable err) {
        reachable = false;
        LOGGER.debug("LAN+ backend request failed (local-only): {}", err.toString());
    }

    private Void onErrorVoid(Throwable err) {
        onError(err);
        return null;
    }

    /** Opens the realtime WebSocket. Single-flight (no overlapping attempts) and re-entrant-safe. */
    private void openSocket() {
        if (!eventsEnabled || !configured() || webSocket != null) {
            return;
        }
        if (!connecting.compareAndSet(false, true)) {
            return;
        }
        URI uri = URI.create(toWebSocketUrl(base()) + "/events");
        http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(uri, new EventSocketListener(eventsUuid, eventsListener))
                .whenComplete((ws, err) -> {
                    connecting.set(false);
                    if (err != null) {
                        LOGGER.warn("LAN+ WebSocket connect failed: {}", err.toString());
                        scheduleReconnect();
                    } else {
                        this.webSocket = ws;
                        reconnectAttempts.set(0);
                    }
                });
    }

    /** Schedules a single backoff reconnect, unless one is already pending or reconnection is disabled. */
    private void scheduleReconnect() {
        if (!eventsEnabled || !configured()) {
            return;
        }
        if (!reconnectPending.compareAndSet(false, true)) {
            return;
        }
        long delay = backoffMillis(reconnectAttempts.getAndIncrement());
        LOGGER.info("LAN+ WebSocket reconnecting in {} ms", delay);
        scheduler.schedule(() -> {
            reconnectPending.set(false);
            openSocket();
        }, delay, TimeUnit.MILLISECONDS);
    }

    /** Exponential backoff (2s, 4s, 8s … capped at 60s) with ±20% jitter to avoid thundering herds. */
    private static long backoffMillis(int attempt) {
        long delay = Math.min(RECONNECT_MAX_MS, RECONNECT_BASE_MS << Math.min(attempt, 5));
        return (long) (delay * (0.8 + Math.random() * 0.4));
    }

    private static String toWebSocketUrl(String httpBase) {
        if (httpBase.startsWith("https://")) {
            return "wss://" + httpBase.substring("https://".length());
        }
        if (httpBase.startsWith("http://")) {
            return "ws://" + httpBase.substring("http://".length());
        }
        return httpBase;
    }

    private final class EventSocketListener implements WebSocket.Listener {

        private final UUID uuid;
        private final BackendEventListener listener;
        private final StringBuilder buffer = new StringBuilder();

        EventSocketListener(UUID uuid, BackendEventListener listener) {
            this.uuid = uuid;
            this.listener = listener;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            reachable = true;
            webSocket.sendText(GSON.toJson(Map.of("type", "AUTH", "uuid", uuid.toString())), true);
            webSocket.request(1);
            // Resync after a (re)connection — events may have been missed while disconnected.
            listener.onConnected();
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                dispatch(webSocket, message);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            LOGGER.warn("LAN+ WebSocket error: {}", error.toString());
            reachable = false;
            HttpLanPlusNetwork.this.webSocket = null;
            scheduleReconnect();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            reachable = false;
            HttpLanPlusNetwork.this.webSocket = null;
            // Reconnect unless this was an intentional disconnect() (which clears eventsEnabled first).
            scheduleReconnect();
            return null;
        }

        private void dispatch(WebSocket webSocket, String message) {
            try {
                JsonObject obj = JsonParser.parseString(message).getAsJsonObject();
                String type = obj.has("type") ? obj.get("type").getAsString() : "";
                switch (type) {
                    case "PRESENCE_UPDATE" -> {
                        JsonObject d = obj.getAsJsonObject("data");
                        listener.onPresenceUpdate(new PresenceUpdate(
                                UUID.fromString(d.get("uuid").getAsString()),
                                Connectivity.valueOf(d.get("connectivity").getAsString()),
                                optionalEnum(d, "state"),
                                optionalString(d, "worldName"),
                                optionalString(d, "joinCode")));
                    }
                    case "FRIEND_STARTED_HOSTING" -> listener.onFriendStartedHosting(
                            UUID.fromString(obj.get("uuid").getAsString()),
                            optionalString(obj, "joinCode"));
                    case "PING" -> webSocket.sendText(GSON.toJson(Map.of("type", "PONG")), true);
                    default -> { /* unknown frame: ignored, not accepted as core logic (PROTOCOL.md) */ }
                }
            } catch (RuntimeException e) {
                LOGGER.warn("LAN+ failed to handle WebSocket message: {}", e.toString());
            }
        }

        private GameplayState optionalEnum(JsonObject obj, String key) {
            return obj.has(key) && !obj.get(key).isJsonNull()
                    ? GameplayState.valueOf(obj.get(key).getAsString())
                    : null;
        }

        private String optionalString(JsonObject obj, String key) {
            return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
        }
    }
}
