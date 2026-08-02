package dev.bgame.lanplus.relay;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class RelayServer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private RelayServer() {}

    public static void main(String[] args) throws Exception {
        RelayConfig cfg = RelayConfig.fromEnv();
        ExecutorService pool = Executors.newCachedThreadPool(daemon("relay-worker"));
        ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(daemon("relay-sched"));

        RoutingTable table = new RoutingTable();
        TicketValidator validator = new TicketValidator(cfg);
        GuestTokenValidator guestTokens = new GuestTokenValidator(cfg);
        RateLimiter limiter = new RateLimiter(cfg.mcRatePerMin);
        ControlListener control = new ControlListener(table, validator, pool);
        MinecraftListener minecraft = new MinecraftListener(table, limiter, guestTokens);

        ServerSocket relaySocket = openRelaySocket(cfg);
        relaySocket.bind(cfg.relayBind);
        ServerSocket mcSocket = new ServerSocket();
        mcSocket.setReuseAddress(true);
        mcSocket.bind(cfg.mcBind);

        acceptLoop(relaySocket, control::handle, pool, "relay-accept");
        acceptLoop(mcSocket, minecraft::handle, pool, "mc-accept");

        sched.scheduleAtFixedRate(table::pingAll, 15, 15, TimeUnit.SECONDS);
        sched.scheduleAtFixedRate(() -> {
            table.sweep(10_000);
            limiter.sweep();
        }, 5, 5, TimeUnit.SECONDS);

        log("LAN+ relay up — control " + cfg.relayBind + (cfg.tls ? " (TLS)" : " (PLAINTEXT)")
                + ", minecraft " + cfg.mcBind
                + (cfg.noAuth ? ", NO_AUTH dev mode, base domain " + cfg.baseDomain
                              : ", backend " + cfg.backendUrl));
        Thread.currentThread().join(); // run forever
    }

    private static ServerSocket openRelaySocket(RelayConfig cfg) throws Exception {
        if (!cfg.tls) {
            ServerSocket s = new ServerSocket();
            s.setReuseAddress(true);
            return s;
        }
        SSLContext ctx = Tls.fromPem(cfg);
        SSLServerSocket s = (SSLServerSocket) ctx.getServerSocketFactory().createServerSocket();
        s.setReuseAddress(true);
        return s;
    }

    private static void acceptLoop(ServerSocket server, Consumer<Socket> handler, ExecutorService pool, String name) {
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try {
                    Socket s = server.accept();
                    pool.execute(() -> handler.accept(s));
                } catch (IOException e) {
                    if (!server.isClosed()) {
                        log("accept error on " + name + ": " + e);
                    }
                }
            }
        }, name);
        t.start();
    }

    static void log(String msg) {
        System.out.println(LocalTime.now().format(TIME) + " [relay] " + msg);
    }

    private static ThreadFactory daemon(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
