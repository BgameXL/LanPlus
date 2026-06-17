package dev.bgame.lanplus.relay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;

final class ControlListener {

    private final RoutingTable table;
    private final TicketValidator validator;
    private final ExecutorService pool;

    ControlListener(RoutingTable table, TicketValidator validator, ExecutorService pool) {
        this.table = table;
        this.validator = validator;
        this.pool = pool;
    }

    void handle(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
            InputStream in = socket.getInputStream();
            Map<String, String> msg = Json.parse(Io.readLine(in));
            String type = msg.get("type");
            if ("HELLO".equals(type)) {
                hello(socket, in, msg);
            } else if ("DATA".equals(type)) {
                data(socket, msg);
            } else {
                Pump.closeQuietly(socket);
            }
        } catch (IOException e) {
            Pump.closeQuietly(socket);
        }
    }

    private void hello(Socket socket, InputStream in, Map<String, String> msg) throws IOException {
        OutputStream out = socket.getOutputStream();
        TicketValidator.Result result = validator.validate(msg.get("ticket"));
        if (result == null) {
            reject(out, "invalid ticket");
            Pump.closeQuietly(socket);
            return;
        }
        String domain = result.domain();
        HostSession session = new HostSession(socket, domain, result.requireToken());
        if (!table.register(domain, session)) {
            reject(out, "domain busy");
            Pump.closeQuietly(socket);
            return;
        }
        session.send(Json.obj("type", "ASSIGNED", "domain", domain));
        RelayServer.log("host assigned " + domain + (result.requireToken() ? " (gated)" : "")
                + " (" + socket.getRemoteSocketAddress() + ")");
        try {
            while (Io.readLine(in) != null) {
                // no client-initiated control messages handled yet
            }
        } finally {
            table.unregister(domain, session);
            Pump.closeQuietly(socket);
            RelayServer.log("host gone, freed " + domain);
        }
    }

    private void data(Socket socket, Map<String, String> msg) throws IOException {
        RoutingTable.Pending pending = table.claimPending(msg.get("id"));
        if (pending == null) {
            Pump.closeQuietly(socket);
            return;
        }
        OutputStream out = socket.getOutputStream();
        out.write(pending.handshake); // replay the player's handshake to the host first
        out.flush();
        Pump.bidirectional(pending.player, socket, pool); // player <-> host(data)
    }

    private static void reject(OutputStream out, String reason) {
        try {
            out.write((Json.obj("type", "REJECTED", "reason", reason) + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            // host already gone
        }
    }
}
