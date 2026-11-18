package dev.bgame.lanplus.network;

import dev.bgame.lanplus.api.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HTTPLanPlusNetworkTest {

    @Test
    void connectsOnlyAfterAuthOkAndIgnoresHttpFailures() throws Exception {
        HTTPLanPlusNetwork network = network();
        AtomicInteger connectedEvents = new AtomicInteger();
        network.addEventListener(new LanPlusNetwork.BackendEventListener() {
            @Override
            public void onConnected() {
                connectedEvents.incrementAndGet();
            }
        });
        HTTPLanPlusNetwork.EventSocketListener listener = network.new EventSocketListener("test-token");
        FakeWebSocket socket = new FakeWebSocket();

        listener.onOpen(socket);

        assertFalse(network.isConnected());
        assertTrue(socket.sentText.get(0).contains("\"type\":\"AUTH\""));
        assertTrue(socket.sentText.get(0).contains("test-token"));

        listener.onText(socket, "{\"type\":\"AUTH_OK\"}", true);

        assertTrue(network.isConnected());
        assertEquals(1, connectedEvents.get());
        assertTrue(network.getFriends(UUID.randomUUID()).get(5, TimeUnit.SECONDS).isEmpty());
        assertTrue(network.isConnected());

        listener.onText(socket, "{\"type\":\"AUTH_OK\"}", true);
        assertEquals(1, connectedEvents.get());

        listener.onClose(socket, WebSocket.NORMAL_CLOSURE, "done");
        assertFalse(network.isConnected());
    }

    @Test
    void staleSocketCloseDoesNotDemoteCurrentConnection() {
        HTTPLanPlusNetwork network = network();
        HTTPLanPlusNetwork.EventSocketListener first = network.new EventSocketListener("first-token");
        HTTPLanPlusNetwork.EventSocketListener second = network.new EventSocketListener("second-token");
        FakeWebSocket firstSocket = new FakeWebSocket();
        FakeWebSocket secondSocket = new FakeWebSocket();

        first.onOpen(firstSocket);
        first.onText(firstSocket, "{\"type\":\"AUTH_OK\"}", true);
        second.onOpen(secondSocket);
        second.onText(secondSocket, "{\"type\":\"AUTH_OK\"}", true);

        first.onClose(firstSocket, WebSocket.NORMAL_CLOSURE, "stale");
        assertTrue(network.isConnected());

        second.onClose(secondSocket, WebSocket.NORMAL_CLOSURE, "current");
        assertFalse(network.isConnected());
    }

    private static HTTPLanPlusNetwork network() {
        PlayerIdentity identity = new PlayerIdentity(UUID.randomUUID(), "TestPlayer");
        return new HTTPLanPlusNetwork(() -> "http://127.0.0.1:1", () -> identity, new MinecraftAuth() {
            @Override
            public String username() {
                return identity.username();
            }

            @Override
            public boolean isPremium() {
                return false;
            }

            @Override
            public void joinServer(String serverId) {
            }
        });
    }

    private static final class FakeWebSocket implements WebSocket {

        private final List<String> sentText = new ArrayList<>();
        private boolean inputClosed;
        private boolean outputClosed;

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            sentText.add(data.toString());
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            outputClosed = true;
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
        }

        @Override
        public String getSubprotocol() {
            return "";
        }

        @Override
        public boolean isOutputClosed() {
            return outputClosed;
        }

        @Override
        public boolean isInputClosed() {
            return inputClosed;
        }

        @Override
        public void abort() {
            inputClosed = true;
            outputClosed = true;
        }
    }
}
