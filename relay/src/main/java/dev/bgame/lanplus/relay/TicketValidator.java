package dev.bgame.lanplus.relay;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class TicketValidator {

    record Result(String domain, boolean requireToken) {}

    private final RelayConfig cfg;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    TicketValidator(RelayConfig cfg) {
        this.cfg = cfg;
    }

    Result validate(String ticket) {
        if (cfg.noAuth) {
            return new Result(Wordlist.randomDomain(cfg.baseDomain), false);
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
            var json = Json.parse(resp.body());
            String domain = MinecraftHandshake.normalize(json.get("domain"));
            boolean requireToken = "true".equals(json.get("requireToken"));
            return domain == null ? null : new Result(domain, requireToken);
        } catch (Exception e) {
            RelayServer.log("ticket validation failed: " + e);
            return null;
        }
    }
}
