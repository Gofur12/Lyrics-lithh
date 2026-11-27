package dev.lyricsflow;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Loads private service credentials from the instance-local lyricsflow/services.properties file. */
final class ServiceConfig {
    private static final String FILE_NAME = "services.properties";
    private static volatile Properties values = new Properties();

    private ServiceConfig() {}

    static void load() {
        Path file = configPath();
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                String template = "# Keep this file private. Do not upload it or share its contents.\n"
                        + "youtube_api_key=\n"
                        + "spotify_client_id=\n";
                Files.writeString(file, template, StandardCharsets.UTF_8);
            }
            Properties loaded = new Properties();
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                loaded.load(reader);
            }
            if (loaded.getProperty("spotify_client_id", "").isBlank()) {\n                loaded.setProperty("spotify_client_id", "274e902910c045aa812c83adfd281356");\n                try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { loaded.store(writer, "LyricsFlow service configuration"); }\n            }\n            values = loaded;
        } catch (IOException exception) {
            values = new Properties();
        }
    }

    static String youtubeApiKey() {
        return values.getProperty("youtube_api_key", "").trim();
    }

    static String spotifyClientId() {
        return values.getProperty("spotify_client_id", "").trim();
    }

    static Path configPath() {
        return MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("lyricsflow").resolve(FILE_NAME);
    }

    static boolean hasYoutubeKey() { return !youtubeApiKey().isBlank(); }
    static boolean hasSpotifyClientId() { return !spotifyClientId().isBlank(); }
}
