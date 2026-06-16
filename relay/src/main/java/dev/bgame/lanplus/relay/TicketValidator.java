package dev.bgame.lanplus.relay;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class TicketValidator {

    private final RelayConfig cfg;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    TicketValidator(RelayConfig cfg) {
        this.cfg = cfg;
    }

    String validate(String ticket) {
        if (cfg.noAuth) {
            return Wordlist.randomDomain(cfg.baseDomain);
        }
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(
                            cfg.backendUrl + "/relay/validate?ticket="
                                    + URLEncoder.encode(ticket, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return null;
            }
            String domain = Json.parse(resp.body()).get("domain");
            return domain == null ? null : MinecraftHandshake.normalize(domain);
        } catch (Exception e) {
            RelayServer.log("ticket validation failed: " + e);
            return null;
        }
    }
}
