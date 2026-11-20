package dev.bgame.lanplus.network;

import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.HostAccessMode;
import dev.bgame.lanplus.api.PresenceSnapshot;
import dev.bgame.lanplus.api.RelayTicket;
import dev.bgame.lanplus.api.SkinRef;
import dev.bgame.lanplus.presence.PresenceManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayHostingCoordinatorTest {

    private static final RelayTicket TICKET = new RelayTicket("ticket", "relay.example.test", 443,
            "world.example.test", 60);

    @Test
    void opensLocalLanPortAndPublishesRelayAddress() {
        FakePresence presence = new FakePresence();
        FakeTunnel tunnel = new FakeTunnel(CompletableFuture.completedFuture("world.example.test"));
        coordinator(presence, tunnel, true);

        presence.publish(snapshot(GameplayState.HOSTING, "localhost:25565"));

        assertEquals(1, tunnel.openCalls);
        assertEquals(25565, tunnel.localPort);
        assertEquals(TICKET, tunnel.ticket);
        assertEquals("world.example.test", presence.current().address());
        assertTrue(tunnel.isOpen());
    }

    @Test
    void closesRelayWhenHostingEnds() {
        FakePresence presence = new FakePresence();
        FakeTunnel tunnel = new FakeTunnel(CompletableFuture.completedFuture("world.example.test"));
        coordinator(presence, tunnel, true);
        presence.publish(snapshot(GameplayState.HOSTING, "127.0.0.1:25565"));

        presence.publish(snapshot(GameplayState.MENU, null));

        assertEquals(1, tunnel.closeCalls);
        assertFalse(tunnel.isOpen());
    }

    @Test
    void ignoresDisabledRelayAndNonLoopbackAddresses() {
        FakePresence disabledPresence = new FakePresence();
        FakeTunnel disabledTunnel = new FakeTunnel(CompletableFuture.completedFuture("unused"));
        coordinator(disabledPresence, disabledTunnel, false);
        disabledPresence.publish(snapshot(GameplayState.HOSTING, "localhost:25565"));

        FakePresence remotePresence = new FakePresence();
        FakeTunnel remoteTunnel = new FakeTunnel(CompletableFuture.completedFuture("unused"));
        coordinator(remotePresence, remoteTunnel, true);
        remotePresence.publish(snapshot(GameplayState.HOSTING, "192.0.2.10:25565"));

        assertEquals(0, disabledTunnel.openCalls);
        assertEquals(0, remoteTunnel.openCalls);
    }

    @Test
    void discardsTunnelThatFinishesAfterHostingEnds() {
        CompletableFuture<String> opening = new CompletableFuture<>();
        FakePresence presence = new FakePresence();
        FakeTunnel tunnel = new FakeTunnel(opening);
        coordinator(presence, tunnel, true);
        presence.publish(snapshot(GameplayState.HOSTING, "localhost:25565"));

        presence.publish(snapshot(GameplayState.MENU, null));
        opening.complete("late.example.test");

        assertEquals(GameplayState.MENU, presence.current().state());
        assertFalse(tunnel.isOpen());
        assertTrue(tunnel.closeCalls >= 1);
    }

    @Test
    void staleTunnelCannotReplaceAReopenedWorld() {
        CompletableFuture<String> first = new CompletableFuture<>();
        CompletableFuture<String> second = new CompletableFuture<>();
        FakePresence presence = new FakePresence();
        FakeTunnel tunnel = new FakeTunnel(first, second);
        coordinator(presence, tunnel, true);

        presence.publish(snapshot(GameplayState.HOSTING, "localhost:25565"));
        presence.publish(snapshot(GameplayState.MENU, null));
        presence.publish(snapshot(GameplayState.HOSTING, "localhost:25566"));
        first.complete("stale.example.test");

        assertEquals("localhost:25566", presence.current().address());
        second.complete("current.example.test");
        assertEquals("current.example.test", presence.current().address());
        assertEquals(2, tunnel.openCalls);
    }

    private static RelayHostingCoordinator coordinator(FakePresence presence, FakeTunnel tunnel, boolean enabled) {
        return new RelayHostingCoordinator(presence, tunnel, null, () -> enabled, () -> false, () -> TICKET);
    }

    private static PresenceSnapshot snapshot(GameplayState state, String address) {
        return new PresenceSnapshot(state, state == GameplayState.HOSTING ? "Test world" : null, address,
                null, null, null, HostAccessMode.EVERYONE, Set.of(), null, null, false);
    }

    private static final class FakeTunnel implements RelayTunnel {

        private final List<CompletableFuture<String>> results;
        private int openCalls;
        private int closeCalls;
        private int localPort;
        private RelayTicket ticket;
        private boolean open;
        private int generation;

        @SafeVarargs
        private FakeTunnel(CompletableFuture<String>... results) {
            this.results = List.of(results);
        }

        @Override
        public CompletableFuture<String> open(int localPort, RelayTicket ticket) {
            CompletableFuture<String> result = results.get(Math.min(openCalls, results.size() - 1));
            int attempt = ++generation;
            openCalls++;
            this.localPort = localPort;
            this.ticket = ticket;
            return result.whenComplete((domain, error) -> {
                if (generation == attempt) {
                    open = error == null && domain != null;
                }
            });
        }

        @Override
        public void close() {
            generation++;
            closeCalls++;
            open = false;
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }

    private static final class FakePresence implements PresenceManager {

        private PresenceSnapshot current = snapshot(GameplayState.MENU, null);
        private PresenceListener listener;

        private void publish(PresenceSnapshot snapshot) {
            current = snapshot;
            listener.onPresenceChanged(snapshot);
        }

        @Override
        public PresenceSnapshot current() {
            return current;
        }

        @Override
        public void updateState(GameplayState state, String worldName, String address) {
            current = new PresenceSnapshot(state, worldName, address, current.joinCode(), current.skin(),
                    current.modpackId(), current.accessMode(), current.allowedUuids(), current.gameMode(),
                    current.difficulty(), current.allowCommands());
            listener.onPresenceChanged(current);
        }

        @Override
        public void setJoinCode(String joinCode, HostAccessMode accessMode, Set<UUID> allowedUuids) {
        }

        @Override
        public void updateSkin(SkinRef skin) {
        }

        @Override
        public void updateModpack(String modpackId) {
        }

        @Override
        public void updateWorld(String gameMode, String difficulty, boolean allowCommands) {
        }

        @Override
        public void heartbeat() {
        }

        @Override
        public void addListener(PresenceListener listener) {
            this.listener = listener;
        }
    }
}
