package dev.lyricsflow;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

/** Spotify public-client integration using Authorization Code with PKCE. */
final class SpotifyService {
    private static final String REDIRECT = "http://127.0.0.1:8888/callback";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String SEARCH_URL = "https://api.spotify.com/v1/search";
    private static volatile String accessToken = "";
    private static volatile String refreshToken = "";
    private static volatile long expiresAt;
    private static volatile String status = "Belum terhubung";
    private static volatile boolean loggingIn;
    private static final SecureRandom RANDOM = new SecureRandom();

    static final class Track {
        final String name, artist, album, image, uri;
        Track(String name, String artist, String album, String image, String uri) {
            this.name = name; this.artist = artist; this.album = album; this.image = image; this.uri = uri;
        }
    }

    private SpotifyService() {}

    static String status() { return status; }
    static boolean connected() { return !accessToken.isBlank() && System.currentTimeMillis() < expiresAt; }
    static boolean loggingIn() { return loggingIn; }

    static synchronized void login() {
        if (loggingIn) return;
        String clientId = ServiceConfig.spotifyClientId();
        if (clientId.isBlank()) { status = "Client ID Spotify belum diatur"; return; }
        loggingIn = true;
        status = "Menunggu login Spotify di browser...";
        Thread worker = new Thread(() -> {
            try {
                String verifier = randomString(64);
                String challenge = Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
                String state = randomString(32);
                try (ServerSocket server = new ServerSocket(8888, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
                    server.setSoTimeout(180000);
                    String authorize = "https://accounts.spotify.com/authorize?response_type=code&client_id="
                            + enc(clientId) + "&redirect_uri=" + enc(REDIRECT)
                            + "&code_challenge_method=S256&code_challenge=" + enc(challenge)
                            + "&state=" + enc(state);
                    net.minecraft.util.Util.getOperatingSystem().open(authorize);
                    try (Socket socket = server.accept()) {
                        socket.setSoTimeout(10000);
                        String request = readRequestLine(socket.getInputStream());
                        String target = request.split(" ")[1];
                        String query = target.contains("?") ? target.substring(target.indexOf('?') + 1) : "";
                        java.util.Map<String,String> params = parseQuery(query);
                        String response = params.getOrDefault("code", "");
                        if (!state.equals(params.get("state")) || response.isBlank()) {
                            writeResponse(socket.getOutputStream(), "Login Spotify dibatalkan atau state tidak cocok. Tutup tab ini.");
                            throw new IllegalStateException("Callback Spotify tidak valid");
                        }
                        writeResponse(socket.getOutputStream(), "Spotify berhasil dihubungkan. Kamu bisa kembali ke Minecraft.");
                        exchangeCode(response, verifier, clientId);
                    }
                }
                status = "Terhubung ke Spotify";
            } catch (Exception e) {
                status = "Login gagal: " + e.getMessage();
            } finally {
                loggingIn = false;
            }
        }, "LyricsFlow-Spotify-OAuth");
        worker.setDaemon(true);
        worker.start();
    }

    static void search(String query, java.util.function.Consumer<List<Track>> callback) {
        Thread worker = new Thread(() -> {
            List<Track> tracks = new ArrayList<>();
            try {
                ensureToken();
                if (accessToken.isBlank()) throw new IllegalStateException("Hubungkan Spotify dulu");
                String address = SEARCH_URL + "?q=" + enc(query) + "&type=track&limit=10";
                HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(12000); c.setReadTimeout(12000);
                c.setRequestProperty("Authorization", "Bearer " + accessToken);
                int code = c.getResponseCode();
                String body = readBody(c, code);
                c.disconnect();
                if (code < 200 || code >= 300) throw new IllegalStateException("Spotify API HTTP " + code);
                JsonArray items = JsonParser.parseString(body).getAsJsonObject()
                        .getAsJsonObject("tracks").getAsJsonArray("items");
                for (int i = 0; i < items.size(); i++) {
                    JsonObject item = items.get(i).getAsJsonObject();
                    StringBuilder artists = new StringBuilder();
                    JsonArray names = item.getAsJsonArray("artists");
                    for (int a = 0; a < names.size(); a++) {
                        if (a > 0) artists.append(", ");
                        artists.append(names.get(a).getAsJsonObject().get("name").getAsString());
                    }
                    JsonObject albumObj = item.getAsJsonObject("album");
                    String image = "";
                    JsonArray images = albumObj.getAsJsonArray("images");
                    if (images.size() > 0) image = images.get(0).getAsJsonObject().get("url").getAsString();
                    tracks.add(new Track(item.get("name").getAsString(), artists.toString(),
                            albumObj.get("name").getAsString(), image, item.get("uri").getAsString()));
                }
                status = tracks.isEmpty() ? "Tidak ada lagu ditemukan" : "Ditemukan " + tracks.size() + " lagu";
            } catch (Exception e) {
                status = "Pencarian gagal: " + e.getMessage();
            }
            callback.accept(tracks);
        }, "LyricsFlow-Spotify-Search");
        worker.setDaemon(true); worker.start();
    }

    private static synchronized void ensureToken() throws Exception {
        if (!accessToken.isBlank() && System.currentTimeMillis() < expiresAt - 60000) return;
        if (refreshToken.isBlank()) { accessToken = ""; return; }
        String data = "grant_type=refresh_token&refresh_token=" + enc(refreshToken)
                + "&client_id=" + enc(ServiceConfig.spotifyClientId());
        JsonObject token = postToken(data);
        applyToken(token);
    }

    private static void exchangeCode(String code, String verifier, String clientId) throws Exception {
        String data = "grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(REDIRECT)
                + "&client_id=" + enc(clientId) + "&code_verifier=" + enc(verifier);
        applyToken(postToken(data));
    }

    private static JsonObject postToken(String data) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(TOKEN_URL).openConnection();
        c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setConnectTimeout(12000); c.setReadTimeout(12000);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream out = c.getOutputStream()) { out.write(data.getBytes(StandardCharsets.UTF_8)); }
        int code = c.getResponseCode();
        String body = readBody(c, code); c.disconnect();
        if (code < 200 || code >= 300) throw new IllegalStateException("Token HTTP " + code);
        return JsonParser.parseString(body).getAsJsonObject();
    }

    private static void applyToken(JsonObject token) throws Exception {
        accessToken = token.get("access_token").getAsString();
        if (token.has("refresh_token")) refreshToken = token.get("refresh_token").getAsString();
        expiresAt = System.currentTimeMillis() + token.get("expires_in").getAsLong() * 1000L;
        saveTokens();
    }

    private static Path tokenFile() { return ServiceConfig.configPath().resolveSibling("spotify-token.properties"); }
    private static synchronized void saveTokens() throws Exception {
        Properties p = new Properties(); p.setProperty("refresh_token", refreshToken);
        try (OutputStream out = Files.newOutputStream(tokenFile())) { p.store(out, "Private Spotify refresh token. Do not share."); }
    }

    private static String readBody(HttpURLConnection c, int code) throws Exception {
        try (InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out); return out.toString(StandardCharsets.UTF_8);
        }
    }
    private static String readRequestLine(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); int b;
        while ((b = in.read()) != -1 && b != '\n') if (b != '\r') out.write(b);
        return out.toString(StandardCharsets.US_ASCII);
    }
    private static void writeResponse(OutputStream out, String message) throws Exception {
        byte[] body = ("<html><meta charset=utf-8><body><h2>" + message + "</h2></body></html>").getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\nContent-Length: "
                + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(body); out.flush();
    }
    private static java.util.Map<String,String> parseQuery(String q) throws Exception {
        java.util.Map<String,String> map = new java.util.HashMap<>();
        for (String pair : q.split("&")) {
            String[] kv = pair.split("=", 2);
            map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return map;
    }
    private static String randomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
        StringBuilder b = new StringBuilder(length);
        for (int i = 0; i < length; i++) b.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return b.toString();
    }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
