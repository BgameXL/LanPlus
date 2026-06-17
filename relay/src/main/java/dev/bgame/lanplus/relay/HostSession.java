package dev.bgame.lanplus.relay;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

final class HostSession {

    private final Socket control;
    private final OutputStream out;
    private final String domain;
    private final boolean requireToken;

    HostSession(Socket control, String domain, boolean requireToken) throws IOException {
        this.control = control;
        this.out = control.getOutputStream();
        this.domain = domain;
        this.requireToken = requireToken;
    }

    String domain() {
        return domain;
    }

    boolean requireToken() {
        return requireToken;
    }

    synchronized boolean send(String jsonLine) {
        try {
            out.write((jsonLine + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (IOException e) {
            Pump.closeQuietly(control);
            return false;
        }
    }
}
