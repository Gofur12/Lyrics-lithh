package dev.lyricsflow;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Reads playback state from the Android companion's loopback server, with legacy file fallback. */
public final class NowPlayingBridge {
    private static final long MAX_AGE_MS = 15_000L;
    private NowPlayingBridge() {}

    public static State read(Path fallbackFile) {
        State fromCompanion = readLocalServer();
        return fromCompanion != null ? fromCompanion : readFile(fallbackFile);
    }

    private static State readLocalServer() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("http://127.0.0.1:8765/nowplaying").openConnection();
            connection.setConnectTimeout(250);
            connection.setReadTimeout(250);
            connection.setUseCaches(false);
            connection.setRequestMethod("GET");
            if (connection.getResponseCode() != 200) return null;
            try (InputStream input = connection.getInputStream()) {
                Properties properties = new Properties();
                properties.load(input);
                return parse(properties, true);
            }
        } catch (IOException | IllegalArgumentException exception) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static State readFile(Path file) {
        if (!Files.isRegularFile(file)) return null;
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return parse(properties, false);
        } catch (IOException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static State parse(Properties properties, boolean urlEncoded) {
        long updatedAt = Long.parseLong(properties.getProperty("updated_at_ms", "0").trim());
        long now = System.currentTimeMillis();
        if (updatedAt <= 0 || Math.abs(now - updatedAt) > MAX_AGE_MS) return null;
        long position = Math.max(0L, Long.parseLong(properties.getProperty("position_ms", "0").trim()));
        boolean playing = Boolean.parseBoolean(properties.getProperty("playing", "false").trim());
        String title = properties.getProperty("title", "").trim();
        String artist = properties.getProperty("artist", "").trim();
        if (urlEncoded) {
            title = URLDecoder.decode(title, StandardCharsets.UTF_8);
            artist = URLDecoder.decode(artist, StandardCharsets.UTF_8);
        }
        return new State(title, artist, position, playing);
    }

    public record State(String title, String artist, long positionMs, boolean playing) {}
}
