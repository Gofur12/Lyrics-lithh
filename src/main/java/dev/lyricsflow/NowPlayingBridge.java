package dev.lyricsflow;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Reads optional playback metadata from lyricsflow/nowplaying.properties. */
public final class NowPlayingBridge {
    private static final long MAX_AGE_MS = 15_000L;

    private NowPlayingBridge() {}

    public static State read(Path file) {
        if (!Files.isRegularFile(file)) return null;
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return parse(properties);
        } catch (IOException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static State parse(Properties properties) {
        try {
            long updatedAt = Long.parseLong(properties.getProperty("updated_at_ms", "0").trim());
            long now = System.currentTimeMillis();
            if (updatedAt <= 0 || Math.abs(now - updatedAt) > MAX_AGE_MS) return null;

            long position = Math.max(0L,
                    Long.parseLong(properties.getProperty("position_ms", "0").trim()));
            boolean playing = Boolean.parseBoolean(properties.getProperty("playing", "false").trim());
            String title = properties.getProperty("title", "").trim();
            String artist = properties.getProperty("artist", "").trim();
            return new State(title, artist, position, playing, now);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public record State(String title, String artist, long positionMs, boolean playing, long sampledAtMs) {}
}
