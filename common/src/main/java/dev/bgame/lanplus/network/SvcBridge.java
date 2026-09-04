package dev.bgame.lanplus.network;

import dev.bgame.lanplus.platform.PlatformHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SvcBridge {

    private SvcBridge() {
    }

    public static boolean installed() {
        return Files.isRegularFile(configFile());
    }

    public static void applyVoiceHost(String hostPort) {
        if (hostPort == null || hostPort.isBlank()) {
            return;
        }
        Path file = configFile();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
            boolean replaced = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().startsWith("voice_host=")) {
                    lines.set(i, "voice_host=" + hostPort);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                lines.add("voice_host=" + hostPort);
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
        }
    }

    private static Path configFile() {
        return PlatformHolder.get().getConfigDir().resolve("voicechat").resolve("voicechat-server.properties");
    }
}
