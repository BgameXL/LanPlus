package dev.bgame.lanplus.relay;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

final class HostSession {

    private final Socket control;
    private final OutputStream out;
    private final String domain;

    HostSession(Socket control, String domain) throws IOException {
        this.control = control;
        this.out = control.getOutputStream();
        this.domain = domain;
    }

    String domain() {
        return domain;
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
