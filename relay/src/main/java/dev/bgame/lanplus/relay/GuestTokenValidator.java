package dev.bgame.lanplus.relay;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class GuestTokenValidator {

    private final RelayConfig cfg;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    GuestTokenValidator(RelayConfig cfg) {
        this.cfg = cfg;
    }

    String validate(String token) {
        if (cfg.noAuth || token == null || token.isBlank()) {
            return null;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(
                            cfg.backendUrl + "/relay/guest/validate?token="
                                    + URLEncoder.encode(token, StandardCharsets.UTF_8)))
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
            RelayServer.log("guest token validation failed: " + e);
            return null;
        }
    }
}
