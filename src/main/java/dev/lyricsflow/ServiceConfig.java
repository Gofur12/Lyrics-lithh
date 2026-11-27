package dev.lyricsflow;

import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Loads service configuration from the instance-local lyricsflow folder. */
final class ServiceConfig {
    private static final String FILE_NAME = "services.properties";
    private static volatile Properties values = new Properties();

    private ServiceConfig() {}

    static synchronized void load() {
        Path file = configPath();
        try {
            Files.createDirectories(file.getParent());
            Properties loaded = new Properties();
            if (Files.exists(file)) {
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    loaded.load(reader);
                }
            }
            if (loaded.getProperty("spotify_client_id", "").isBlank()) {
                loaded.setProperty("spotify_client_id", "274e902910c045aa812c83adfd281356");
            }
            if (!loaded.containsKey("youtube_api_key")) {
                loaded.setProperty("youtube_api_key", "AIzaSyAh3L7enEFKDW3GZlb1BDf3_rSakeWvrNk");
            }
            try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                loaded.store(writer, "LyricsFlow service configuration. Keep private; do not share.");
            }
            values = loaded;
        } catch (Exception e) {
            values = new Properties();
            System.err.println("[LyricsFlow] Could not load service config: " + e.getMessage());
        }
    }

    static Path configPath() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("lyricsflow").resolve(FILE_NAME);
    }

    static String spotifyClientId() {
        return values.getProperty("spotify_client_id", "274e902910c045aa812c83adfd281356").trim();
    }

    static synchronized void setYoutubeApiKey(String key) {
        load();
        String clean = key == null ? "" : key.trim();
        values.setProperty("youtube_api_key", clean);
        Path file = configPath();
        try {
            Files.createDirectories(file.getParent());
            try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                values.store(writer, "LyricsFlow service configuration. Keep private; do not share.");
            }
        } catch (Exception e) {
            System.err.println("[LyricsFlow] Could not save YouTube API key: " + e.getMessage());
        }
    }

    static String youtubeApiKey() {
        return values.getProperty("youtube_api_key", "").trim();
    }
}
