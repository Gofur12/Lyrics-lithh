package dev.lyricsflow;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Persistent local playlist metadata. Audio playback is provided by a source integration. */
final class PlaylistStore {
    private static final Path FILE = Path.of("lyricsflow", "playlists.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Playlist> PLAYLISTS = new ArrayList<>();
    private static boolean loaded;

    static final class Playlist {
        String name;
        final List<String> tracks = new ArrayList<>();
        Playlist(String name) { this.name = name; }
    }

    private PlaylistStore() {}

    static List<Playlist> all() {
        load();
        return Collections.unmodifiableList(PLAYLISTS);
    }

    static Playlist selected(int index) {
        load();
        return PLAYLISTS.isEmpty() ? null : PLAYLISTS.get(Math.floorMod(index, PLAYLISTS.size()));
    }

    static int size() { load(); return PLAYLISTS.size(); }

    static boolean create(String name) {
        load();
        String clean = clean(name);
        if (clean.isEmpty() || exists(clean)) return false;
        PLAYLISTS.add(new Playlist(clean));
        save();
        return true;
    }

    static boolean rename(int index, String name) {
        load();
        String clean = clean(name);
        if (index < 0 || index >= PLAYLISTS.size() || clean.isEmpty()) return false;
        for (int i = 0; i < PLAYLISTS.size(); i++)
            if (i != index && PLAYLISTS.get(i).name.equalsIgnoreCase(clean)) return false;
        PLAYLISTS.get(index).name = clean;
        save();
        return true;
    }

    static boolean delete(int index) {
        load();
        if (index < 0 || index >= PLAYLISTS.size()) return false;
        PLAYLISTS.remove(index);
        save();
        return true;
    }

    static boolean addTrack(int index, String title) {
        load();
        String clean = clean(title);
        if (index < 0 || index >= PLAYLISTS.size() || clean.isEmpty()) return false;
        Playlist playlist = PLAYLISTS.get(index);
        if (playlist.tracks.stream().anyMatch(t -> t.equalsIgnoreCase(clean))) return false;
        playlist.tracks.add(clean);
        save();
        return true;
    }

    static boolean removeTrack(int playlistIndex, int trackIndex) {
        load();
        if (playlistIndex < 0 || playlistIndex >= PLAYLISTS.size()) return false;
        List<String> tracks = PLAYLISTS.get(playlistIndex).tracks;
        if (trackIndex < 0 || trackIndex >= tracks.size()) return false;
        tracks.remove(trackIndex);
        save();
        return true;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replace("\\n", " ").replace("\\r", " ");
    }

    private static boolean exists(String name) {
        return PLAYLISTS.stream().anyMatch(p -> p.name.equalsIgnoreCase(name));
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path file = MinecraftClientPath.resolve(FILE);
            if (!Files.isRegularFile(file)) return;
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonArray()) return;
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                String name = object.has("name") ? object.get("name").getAsString() : "";
                if (clean(name).isEmpty() || exists(clean(name))) continue;
                Playlist playlist = new Playlist(clean(name));
                JsonArray tracks = object.has("tracks") && object.get("tracks").isJsonArray()
                        ? object.getAsJsonArray("tracks") : new JsonArray();
                for (JsonElement track : tracks)
                    if (track.isJsonPrimitive()) playlist.tracks.add(clean(track.getAsString()));
                PLAYLISTS.add(playlist);
            }
        } catch (Exception ignored) {
            PLAYLISTS.clear();
        }
    }

    private static void save() {
        JsonArray root = new JsonArray();
        for (Playlist playlist : PLAYLISTS) {
            JsonObject object = new JsonObject();
            object.addProperty("name", playlist.name);
            JsonArray tracks = new JsonArray();
            for (String track : playlist.tracks) tracks.add(track);
            object.add("tracks", tracks);
            root.add(object);
        }
        try {
            Path file = MinecraftClientPath.resolve(FILE);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    /** Keeps storage rooted in Minecraft's run directory without loading client classes here. */
    private static final class MinecraftClientPath {
        static Path resolve(Path relative) {
            return net.minecraft.client.MinecraftClient.getInstance().runDirectory.toPath().resolve(relative);
        }
    }
}
