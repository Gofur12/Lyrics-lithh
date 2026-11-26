package dev.lyricsflow;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Persistent playlist metadata. Track paths are relative to the lyricsflow directory. */
final class PlaylistStore {
    private static final Path FILE = Path.of("lyricsflow", "playlists.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Playlist> PLAYLISTS = new ArrayList<>();
    private static boolean loaded;

    static final class Track {
        final String title;
        final String lrcFile;
        Track(String title, String lrcFile) { this.title = title; this.lrcFile = lrcFile; }
    }
    static final class Playlist {
        String name;
        final List<Track> tracks = new ArrayList<>();
        Playlist(String name) { this.name = name; }
    }
    private PlaylistStore() {}
    static List<Playlist> all() { load(); return Collections.unmodifiableList(PLAYLISTS); }
    static Playlist selected(int index) {
        load(); return PLAYLISTS.isEmpty() ? null : PLAYLISTS.get(Math.floorMod(index, PLAYLISTS.size()));
    }
    static int size() { load(); return PLAYLISTS.size(); }
    static boolean create(String name) {
        load(); String clean = clean(name);
        if (clean.isEmpty() || exists(clean)) return false;
        PLAYLISTS.add(new Playlist(clean)); save(); return true;
    }
    static boolean rename(int index, String name) {
        load(); String clean = clean(name);
        if (index < 0 || index >= PLAYLISTS.size() || clean.isEmpty()) return false;
        for (int i = 0; i < PLAYLISTS.size(); i++)
            if (i != index && PLAYLISTS.get(i).name.equalsIgnoreCase(clean)) return false;
        PLAYLISTS.get(index).name = clean; save(); return true;
    }
    static boolean delete(int index) {
        load(); if (index < 0 || index >= PLAYLISTS.size()) return false;
        PLAYLISTS.remove(index); save(); return true;
    }
    /** Input format: title | relative-file.lrc. A title alone uses automatic lookup. */
    static boolean addTrack(int index, String input) {
        load(); if (index < 0 || index >= PLAYLISTS.size()) return false;
        String raw = input == null ? "" : input.trim();
        int separator = raw.indexOf('|');
        String title = clean(separator < 0 ? raw : raw.substring(0, separator));
        String path = separator < 0 ? "" : clean(raw.substring(separator + 1));
        if (title.isEmpty() || (separator >= 0 && !validLrcPath(path))) return false;
        Playlist playlist = PLAYLISTS.get(index);
        if (playlist.tracks.stream().anyMatch(t -> t.title.equalsIgnoreCase(title))) return false;
        playlist.tracks.add(new Track(title, path)); save(); return true;
    }
    static boolean removeTrack(int playlistIndex, int trackIndex) {
        load(); if (playlistIndex < 0 || playlistIndex >= PLAYLISTS.size()) return false;
        List<Track> tracks = PLAYLISTS.get(playlistIndex).tracks;
        if (trackIndex < 0 || trackIndex >= tracks.size()) return false;
        tracks.remove(trackIndex); save(); return true;
    }
    private static boolean validLrcPath(String value) {
        if (value.isBlank() || value.startsWith("/") || value.startsWith("\\\\") || value.contains(":")) return false;
        String normalized = value.replace('\\', '/');
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".lrc")) return false;
        for (String part : normalized.split("/")) if (part.equals("..") || part.isBlank()) return false;
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
                for (JsonElement item : tracks) {
                    if (item.isJsonPrimitive()) {
                        String title = clean(item.getAsString());
                        if (!title.isEmpty()) playlist.tracks.add(new Track(title, ""));
                    } else if (item.isJsonObject()) {
                        JsonObject data = item.getAsJsonObject();
                        String title = clean(data.has("title") ? data.get("title").getAsString() : "");
                        String path = clean(data.has("lrcFile") ? data.get("lrcFile").getAsString() : "");
                        if (!title.isEmpty() && (path.isEmpty() || validLrcPath(path)))
                            playlist.tracks.add(new Track(title, path));
                    }
                }
                PLAYLISTS.add(playlist);
            }
        } catch (Exception ignored) { PLAYLISTS.clear(); }
    }
    private static void save() {
        JsonArray root = new JsonArray();
        for (Playlist playlist : PLAYLISTS) {
            JsonObject object = new JsonObject(); object.addProperty("name", playlist.name);
            JsonArray tracks = new JsonArray();
            for (Track track : playlist.tracks) {
                JsonObject item = new JsonObject();
                item.addProperty("title", track.title); item.addProperty("lrcFile", track.lrcFile);
                tracks.add(item);
            }
            object.add("tracks", tracks); root.add(object);
        }
        try {
            Path file = MinecraftClientPath.resolve(FILE);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }
    private static final class MinecraftClientPath {
        static Path resolve(Path relative) {
            return net.minecraft.client.MinecraftClient.getInstance().runDirectory.toPath().resolve(relative);
        }
    }
}
