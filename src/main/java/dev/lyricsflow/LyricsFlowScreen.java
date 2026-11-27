package dev.lyricsflow;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Main LyricsFlow player shell, drawn with vanilla GUI primitives for Android compatibility. */
public final class LyricsFlowScreen extends Screen {
    private enum Tab {
        PLAYER("Player"), LYRICS("Lirik"), SPOTIFY("Spotify"),
        YOUTUBE("YouTube"), LOCAL("Musik HP"), PLAYLIST("Playlist"), APP("App");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private Tab selectedTab = Tab.PLAYER;
    private int panelLeft, panelTop, panelWidth, panelHeight;
    private int selectedPlaylist;
    private int selectedTrack;
    private TextFieldWidget playlistInput;
    private TextFieldWidget serviceSearchInput;
    private TextFieldWidget youtubeApiKeyInput;
    private final List<YouTubeResult> youtubeResults = new ArrayList<>();
    private String youtubeSearchStatus = "";
    private String selectedYoutubeVideoId = "";
    private String selectedYoutubeVideoTitle = "";
    private boolean youtubeSearching;
    private int youtubeScrollOffset;
    private String youtubeNextPageToken = "";
    private String youtubeLastQuery = "";
    private final Map<String, Identifier> youtubeThumbnails = new HashMap<>();
    private final Set<String> youtubeThumbnailRequests = new HashSet<>();
    private final List<SpotifyService.Track> spotifyResults = new ArrayList<>();
    private String spotifySearchStatus = "Hubungkan Spotify untuk mulai.";

    private static final class YouTubeResult {
        final String videoId;
        final String title;
        final String channel;
        final String thumbnailUrl;
        YouTubeResult(String videoId, String title, String channel, String thumbnailUrl) {
            this.videoId = videoId;
            this.title = title;
            this.channel = channel;
            this.thumbnailUrl = thumbnailUrl;
        }
    }

    public LyricsFlowScreen() { super(Text.literal("LyricsFlow")); }

    @Override
    protected void init() {
        clearChildren();
        panelWidth = Math.min(430, Math.max(260, width - 24));
        panelHeight = Math.min(selectedTab == Tab.YOUTUBE ? 330 : selectedTab == Tab.SPOTIFY ? 280 : selectedTab == Tab.PLAYLIST ? 250 : 230, Math.max(170, height - 36));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;

        int tabY = panelTop + 8;
        int tabX = panelLeft + 10;
        int tabW = Math.max(35, (panelWidth - 20) / Tab.values().length);
        for (Tab tab : Tab.values()) {
            final Tab target = tab;
            addDrawableChild(ButtonWidget.builder(Text.literal(tab.label), button -> {
                selectedTab = target;
                clearAndInit();
            }).dimensions(tabX, tabY, tabW - 2, 20).build());
            tabX += tabW;
        }

        if (selectedTab == Tab.PLAYLIST) {
            initPlaylistControls();
        } else if (selectedTab == Tab.LYRICS) {
            initLyricsControls();
        } else if (selectedTab == Tab.SPOTIFY || selectedTab == Tab.YOUTUBE) {
            initServiceControls();
        } else {
            if (selectedTab == Tab.PLAYER) {
                addDrawableChild(ButtonWidget.builder(Text.literal("Ⅱ / ▶"), button -> {
                    if (LyricsFlowClient.isPlaying()) LyricsFlowClient.pausePlayback();
                    else LyricsFlowClient.startPlayback();
                    clearAndInit();
                }).dimensions(panelLeft + panelWidth / 2 - 68, panelTop + panelHeight - 34, 64, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("-5s"), button -> {
                    LyricsFlowClient.seekBy(-5000L);
                    clearAndInit();
                }).dimensions(panelLeft + panelWidth / 2 - 112, panelTop + panelHeight - 34, 40, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("+5s"), button -> {
                    LyricsFlowClient.seekBy(5000L);
                    clearAndInit();
                }).dimensions(panelLeft + panelWidth / 2 + 72, panelTop + panelHeight - 34, 40, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), button -> {
                    LyricsFlowClient.stopPlayback();
                    clearAndInit();
                }).dimensions(panelLeft + panelWidth / 2 + 4, panelTop + panelHeight - 34, 48, 20).build());
            } else {
                addDrawableChild(ButtonWidget.builder(Text.literal("Ⅱ / ▶"), button -> {
                    if (LyricsFlowClient.isPlaying()) LyricsFlowClient.pausePlayback();
                    else LyricsFlowClient.startPlayback();
                    clearAndInit();
                }).dimensions(panelLeft + panelWidth / 2 - 32, panelTop + panelHeight - 34, 64, 20).build());
                if (selectedTab == Tab.APP) {
                    addDrawableChild(ButtonWidget.builder(Text.literal(
                            LyricsFlowClient.isVisible() ? "Sembunyikan lirik HUD" : "Tampilkan lirik HUD"), button -> {
                        LyricsFlowClient.toggleOverlay();
                        clearAndInit();
                    }).dimensions(panelLeft + 22, panelTop + 104, panelWidth - 44, 22).build());
                }
            }
        }
        if (selectedTab != Tab.PLAYLIST) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Selesai"), button -> close())
                    .dimensions(panelLeft + panelWidth - 58, panelTop + panelHeight - 34, 48, 20).build());
        }
    }

    private void initServiceControls() {
        int x = panelLeft + 18;
        int fieldWidth = panelWidth - 36;
        String initial = !LyricsFlowClient.getLrcTitle().isBlank()
                ? LyricsFlowClient.getLrcTitle() : LyricsFlowClient.getSelectedLocalTitle();

        if (selectedTab == Tab.YOUTUBE) {
            ServiceConfig.load();
            int keyY = panelTop + 76;
            youtubeApiKeyInput = new TextFieldWidget(textRenderer, x, keyY, fieldWidth, 20,
                    Text.literal("YouTube API key"));
            youtubeApiKeyInput.setMaxLength(200);
            youtubeApiKeyInput.setText(ServiceConfig.youtubeApiKey());
            youtubeApiKeyInput.setPlaceholder(Text.literal("Tempel YouTube Data API v3 key"));
            addDrawableChild(youtubeApiKeyInput);

            addDrawableChild(ButtonWidget.builder(Text.literal("Simpan API Key"), button -> {
                ServiceConfig.setYoutubeApiKey(youtubeApiKeyInput.getText());
                youtubeSearchStatus = youtubeApiKeyInput.getText().isBlank()
                        ? "API key dihapus." : "API key tersimpan di perangkat.";
                clearAndInit();
            }).dimensions(x, keyY + 23, fieldWidth, 20).build());

            int queryY = keyY + 49;
            serviceSearchInput = new TextFieldWidget(textRenderer, x, queryY, fieldWidth, 20,
                    Text.literal("Masukkan judul lagu atau artis"));
            serviceSearchInput.setMaxLength(120);
            serviceSearchInput.setText(initial);
            serviceSearchInput.setPlaceholder(Text.literal("Judul lagu atau nama artis"));
            addDrawableChild(serviceSearchInput);

            addDrawableChild(ButtonWidget.builder(Text.literal(youtubeSearching ? "Mencari..." : "Cari di YouTube"), button -> {
                String query = serviceSearchInput.getText().trim();
                if (query.isEmpty() || youtubeSearching) return;
                youtubeLastQuery = query;
                youtubeNextPageToken = "";
                youtubeScrollOffset = 0;
                searchYouTube(query, "", false);
            }).dimensions(x, queryY + 23, fieldWidth, 20).build());

            int resultY = queryY + 49;
            int count = Math.min(3, Math.max(0, youtubeResults.size() - youtubeScrollOffset));
            for (int i = 0; i < count; i++) {
                YouTubeResult result = youtubeResults.get(youtubeScrollOffset + i);
                final YouTubeResult chosen = result;
                String label = trim(result.title + " - " + result.channel, Math.max(12, (fieldWidth - 48) / 6));
                addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
                    selectedYoutubeVideoId = chosen.videoId;
                    selectedYoutubeVideoTitle = chosen.title;
                    youtubeSearchStatus = "Dipilih: " + chosen.title;
                    clearAndInit();
                }).dimensions(x, resultY + i * 22, fieldWidth, 20).build());
            }
        } else {
            int y = panelTop + 91;
            serviceSearchInput = new TextFieldWidget(textRenderer, x, y, fieldWidth, 20,
                    Text.literal("Masukkan judul lagu atau artis"));
            serviceSearchInput.setMaxLength(120);
            serviceSearchInput.setText(initial);
            serviceSearchInput.setPlaceholder(Text.literal("Judul lagu atau nama artis"));
            addDrawableChild(serviceSearchInput);

            addDrawableChild(ButtonWidget.builder(Text.literal(SpotifyService.connected() ? "Terhubung" : SpotifyService.loggingIn() ? "Menunggu login..." : "Hubungkan Spotify"), button -> {
                if (!SpotifyService.connected()) {
                    SpotifyService.login();
                    spotifySearchStatus = SpotifyService.status();
                    clearAndInit();
                }
            }).dimensions(x, y + 26, fieldWidth, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Cari lagu"), button -> {
                String query = serviceSearchInput.getText().trim();
                if (query.isEmpty()) { spotifySearchStatus = "Masukkan judul lagu atau artis."; clearAndInit(); return; }
                spotifySearchStatus = "Mencari di Spotify...";
                SpotifyService.search(query, tracks -> {
                    if (client != null) client.execute(() -> {
                        spotifyResults.clear();
                        spotifyResults.addAll(tracks);
                        spotifySearchStatus = SpotifyService.status();
                        clearAndInit();
                    });
                });
                clearAndInit();
            }).dimensions(x, y + 50, fieldWidth, 20).build());
        }
    }

    private void searchYouTube(String query, String pageToken, boolean append) {
        youtubeSearching = true;
        youtubeSearchStatus = "Menghubungi YouTube...";
        if (!append) {
            youtubeResults.clear();
            clearAndInit();
        }
        Thread worker = new Thread(() -> {
            List<YouTubeResult> found = new ArrayList<>();
            String status;
            String nextToken = "";
            try {
                ServiceConfig.load();
                String key = ServiceConfig.youtubeApiKey();
                if (key.isBlank()) {
                    status = "API key kosong. Isi lyricsflow/services.properties";
                } else {
                    String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
                    String address = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=20&q="
                            + encoded + (pageToken.isBlank() ? "" : "&pageToken=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8))
                            + "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
                    HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.setRequestProperty("Accept", "application/json");
                    int code = connection.getResponseCode();
                    InputStream stream = code >= 200 && code < 300
                            ? connection.getInputStream() : connection.getErrorStream();
                    String body;
                    try (InputStream input = stream) {
                        body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    } finally {
                        connection.disconnect();
                    }
                    if (code < 200 || code >= 300) {
                        JsonObject error = JsonParser.parseString(body).getAsJsonObject();
                        String message = error.has("error")
                                ? error.getAsJsonObject("error").get("message").getAsString()
                                : "HTTP " + code;
                        status = "YouTube API: " + message;
                    } else {
                        JsonObject response = JsonParser.parseString(body).getAsJsonObject();
                        nextToken = response.has("nextPageToken") ? response.get("nextPageToken").getAsString() : "";
                        JsonArray items = response.getAsJsonArray("items");
                        for (int i = 0; i < items.size(); i++) {
                            JsonObject item = items.get(i).getAsJsonObject();
                            JsonObject id = item.getAsJsonObject("id");
                            JsonObject snippet = item.getAsJsonObject("snippet");
                            if (id != null && id.has("videoId") && snippet != null) {
                                String thumbnail = "";
                                if (snippet.has("thumbnails")) {
                                    JsonObject thumbnails = snippet.getAsJsonObject("thumbnails");
                                    JsonObject image = thumbnails.has("medium") ? thumbnails.getAsJsonObject("medium")
                                            : thumbnails.has("default") ? thumbnails.getAsJsonObject("default") : null;
                                    if (image != null && image.has("url")) thumbnail = image.get("url").getAsString();
                                }
                                found.add(new YouTubeResult(id.get("videoId").getAsString(),
                                        snippet.get("title").getAsString(),
                                        snippet.get("channelTitle").getAsString(), thumbnail));
                            }
                        }
                        status = found.isEmpty() ? "Tidak ada hasil." : "Hasil YouTube dimuat: " + found.size();
                    }
                }
            } catch (Exception exception) {
                status = "Gagal mencari: " + exception.getClass().getSimpleName();
            }
            final String finalStatus = status;
            final String finalNextToken = nextToken;
            if (client != null) client.execute(() -> {
                if (!append) {
                    youtubeResults.clear();
                    youtubeScrollOffset = 0;
                }
                youtubeResults.addAll(found);
                youtubeNextPageToken = finalNextToken;
                youtubeSearchStatus = append && !found.isEmpty()
                        ? "Hasil bertambah: " + youtubeResults.size() : finalStatus;
                youtubeSearching = false;
                clearAndInit();
            });
        }, append ? "LyricsFlow-YouTube-Next-Page" : "LyricsFlow-YouTube-Search");
        worker.setDaemon(true);
        worker.start();
    }

    private void initLyricsControls() {
        int x = panelLeft + 12;
        int y = panelTop + 154;
        int gap = 3;
        int buttonWidth = (panelWidth - 24 - gap * 3) / 4;

        addDrawableChild(ButtonWidget.builder(Text.literal("Reload"), b -> {
            LyricsFlowClient.reloadLyrics();
            clearAndInit();
        }).dimensions(x, y, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("-100"), b -> {
            LyricsFlowClient.adjustLyricOffset(-100L);
            clearAndInit();
        }).dimensions(x + buttonWidth + gap, y, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("+100"), b -> {
            LyricsFlowClient.adjustLyricOffset(100L);
            clearAndInit();
        }).dimensions(x + (buttonWidth + gap) * 2, y, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), b -> {
            LyricsFlowClient.resetLyricOffset();
            clearAndInit();
        }).dimensions(x + (buttonWidth + gap) * 3, y, buttonWidth, 20).build());
    }

    private void initPlaylistControls() {
        int x = panelLeft + 12;
        int y = panelTop + 74;
        int fieldWidth = panelWidth - 24;
        playlistInput = new TextFieldWidget(textRenderer, x, y, fieldWidth, 20, Text.literal("Nama playlist / Judul | file.lrc"));
        playlistInput.setMaxLength(100);
        addDrawableChild(playlistInput);

        int gap = 4;
        int buttonWidth = (fieldWidth - gap * 2) / 3;
        addDrawableChild(ButtonWidget.builder(Text.literal("Buat"), b -> {
            if (PlaylistStore.create(playlistInput.getText())) selectedPlaylist = PlaylistStore.size() - 1;
            selectedTrack = 0;
            clearAndInit();
        }).dimensions(x, y + 24, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Ubah nama"), b -> {
            if (PlaylistStore.rename(selectedPlaylist, playlistInput.getText())) clearAndInit();
        }).dimensions(x + buttonWidth + gap, y + 24, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Hapus"), b -> {
            PlaylistStore.delete(selectedPlaylist);
            selectedPlaylist = Math.max(0, Math.min(selectedPlaylist, PlaylistStore.size() - 1));
            selectedTrack = 0;
            clearAndInit();
        }).dimensions(x + (buttonWidth + gap) * 2, y + 24, buttonWidth, 20).build());

        int half = (fieldWidth - gap) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Tambah lagu"), b -> {
            if (PlaylistStore.addTrack(selectedPlaylist, playlistInput.getText())) {
                PlaylistStore.selected(selectedPlaylist);
                selectedTrack = Math.max(0, PlaylistStore.selected(selectedPlaylist).tracks.size() - 1);
                clearAndInit();
            }
        }).dimensions(x, y + 48, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("- Hapus lagu"), b -> {
            PlaylistStore.removeTrack(selectedPlaylist, selectedTrack);
            selectedTrack = Math.max(0, selectedTrack - 1);
            clearAndInit();
        }).dimensions(x + half + gap, y + 48, half, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("‹ Playlist"), b -> {
            if (PlaylistStore.size() > 0) selectedPlaylist = Math.floorMod(selectedPlaylist - 1, PlaylistStore.size());
            selectedTrack = 0;
            clearAndInit();
        }).dimensions(x, y + 72, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Playlist ›"), b -> {
            if (PlaylistStore.size() > 0) selectedPlaylist = Math.floorMod(selectedPlaylist + 1, PlaylistStore.size());
            selectedTrack = 0;
            clearAndInit();
        }).dimensions(x + half + gap, y + 72, half, 20).build());

        int third = (fieldWidth - gap * 2) / 3;
        addDrawableChild(ButtonWidget.builder(Text.literal("‹ Lagu"), b -> {
            PlaylistStore.Playlist p = PlaylistStore.selected(selectedPlaylist);
            if (p != null && !p.tracks.isEmpty()) selectedTrack = Math.floorMod(selectedTrack - 1, p.tracks.size());
            clearAndInit();
        }).dimensions(x, y + 96, third, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Gunakan"), b -> {
            PlaylistStore.Playlist p = PlaylistStore.selected(selectedPlaylist);
            if (p != null && !p.tracks.isEmpty()) {
                selectedTrack = Math.floorMod(selectedTrack, p.tracks.size());
                PlaylistStore.Track track = p.tracks.get(selectedTrack);
                LyricsFlowClient.selectLocalTrack(track.title, track.lrcFile);
                selectedTab = Tab.PLAYER;
                clearAndInit();
            }
        }).dimensions(x + third + gap, y + 96, third, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Lagu ›"), b -> {
            PlaylistStore.Playlist p = PlaylistStore.selected(selectedPlaylist);
            if (p != null && !p.tracks.isEmpty()) selectedTrack = Math.floorMod(selectedTrack + 1, p.tracks.size());
            clearAndInit();
        }).dimensions(x + (third + gap) * 2, y + 96, third, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xF0181B24);
        context.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 2, 0xFF70C7D8);
        context.drawTextWithShadow(textRenderer, "LYRICSFLOW", panelLeft + 12, panelTop + 34, 0xFF70C7D8);
        context.drawTextWithShadow(textRenderer, selectedTab.label, panelLeft + 12, panelTop + 50, 0xFFFFFFFF);

        int contentTop = panelTop + 72;
        int contentBottom = panelTop + panelHeight - 42;
        context.fill(panelLeft + 10, contentTop, panelLeft + panelWidth - 10, contentBottom, 0xFF222733);

        switch (selectedTab) {
            case PLAYER -> renderPlayer(context, contentTop);
            case LYRICS -> renderLyrics(context, contentTop);
            case PLAYLIST -> renderPlaylist(context, contentTop);
            case SPOTIFY -> renderSpotify(context, contentTop);
            case YOUTUBE -> renderYouTube(context, contentTop);
            case LOCAL -> {
                context.drawTextWithShadow(textRenderer, "Metadata pemutar HP melalui file:", panelLeft + 22, contentTop + 18, 0xFFB0B8C5);
                context.drawTextWithShadow(textRenderer, "lyricsflow/nowplaying.properties", panelLeft + 22, contentTop + 32, 0xFF70C7D8);
                context.drawTextWithShadow(textRenderer, "Isi title, artist, position_ms, playing, updated_at_ms.", panelLeft + 22, contentTop + 82, 0xFF8995A7);
                context.drawTextWithShadow(textRenderer, "Perlu aplikasi/alat terpisah untuk memperbarui file ini.", panelLeft + 22, contentTop + 50, 0xFF8995A7);
                context.drawTextWithShadow(textRenderer, "Mod ini hanya membaca metadata, bukan audio HP.", panelLeft + 22, contentTop + 66, 0xFF8995A7);
            }
            case APP -> renderService(context, contentTop, "App", "Pengaturan lengkap tersedia melalui tombol Configure di Mod Menu.");
        }
        super.render(context, mouseX, mouseY, delta);
        if (selectedTab == Tab.YOUTUBE) renderYouTubeThumbnails(context);
    }

    private void loadYouTubeThumbnail(YouTubeResult result) {
        if (result.thumbnailUrl.isBlank() || youtubeThumbnails.containsKey(result.videoId)
                || !youtubeThumbnailRequests.add(result.videoId)) return;
        Thread worker = new Thread(() -> {
            NativeImage image = null;
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(result.thumbnailUrl).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", "LyricsFlow/1.0");
                try (InputStream input = connection.getInputStream()) {
                    image = NativeImage.read(input);
                } finally {
                    connection.disconnect();
                }
            } catch (Exception ignored) {
                // Thumbnail failure must not break search results.
            }
            final NativeImage downloaded = image;
            if (downloaded != null && client != null) client.execute(() -> {
                Identifier textureId = client.getTextureManager().registerDynamicTexture(
                        "lyricsflow_youtube_" + result.videoId, new NativeImageBackedTexture(downloaded));
                youtubeThumbnails.put(result.videoId, textureId);
            });
        }, "LyricsFlow-YouTube-Thumbnail");
        worker.setDaemon(true);
        worker.start();
    }

    private void renderYouTubeThumbnails(DrawContext context) {
        int x = panelLeft + 20 + 2;
        int resultY = panelTop + 76 + 49 + 49;
        for (int i = 0; i < Math.min(3, Math.max(0, youtubeResults.size() - youtubeScrollOffset)); i++) {
            YouTubeResult result = youtubeResults.get(youtubeScrollOffset + i);
            loadYouTubeThumbnail(result);
            Identifier texture = youtubeThumbnails.get(result.videoId);
            if (texture != null) {
                context.drawTexture(texture, x, resultY + i * 22 + 1, 0.0F, 0.0F, 32, 18, 32, 18);
            }
        }
    }

    private void renderPlayer(DrawContext context, int y) {
        int x = panelLeft + 22;
        context.fill(x, y + 8, x + 76, y + 84, 0xFF394252);
        context.drawCenteredTextWithShadow(textRenderer, "♪", x + 38, y + 38, 0xFFB9DDE5);
        String displayTitle = LyricsFlowClient.getLrcTitle().isBlank()
                ? LyricsFlowClient.getSelectedLocalTitle() : LyricsFlowClient.getLrcTitle();
        String displayArtist = LyricsFlowClient.getLrcArtist();
        String displayAlbum = LyricsFlowClient.getLrcAlbum();
        context.drawTextWithShadow(textRenderer, displayTitle.isEmpty() ? "Belum ada lagu" : trim(displayTitle, 30),
                x + 90, y + 12, 0xFFFFFFFF);
        String subtitle = !displayArtist.isBlank() ? displayArtist
                : (LyricsFlowClient.hasExternalPlayer() ? "Pemutar eksternal" : "Timeline lirik internal");
        context.drawTextWithShadow(textRenderer, trim(subtitle, 34), x + 90, y + 27, 0xFFB0B8C5);
        if (!displayAlbum.isBlank()) {
            context.drawTextWithShadow(textRenderer, "Album: " + trim(displayAlbum, 28), x + 90, y + 40, 0xFF8995A7);
        }
        int barLeft = x + 90;
        int barRight = panelLeft + panelWidth - 22;
        int barWidth = Math.max(1, barRight - barLeft);
        long duration = LyricsFlowClient.getLyricsDurationMs();
        long position = LyricsFlowClient.getPlaybackPositionMs();
        long shownPosition = duration > 0 ? Math.min(position, duration) : position;
        int filled = duration > 0 ? (int) (barWidth * (double) shownPosition / duration) : 0;
        context.fill(barLeft, y + 52, barRight, y + 55, 0xFF596273);
        if (filled > 0) context.fill(barLeft, y + 52, barLeft + filled, y + 55, 0xFF70C7D8);
        context.drawTextWithShadow(textRenderer, formatTime(shownPosition), barLeft, y + 62, 0xFFB0B8C5);
        context.drawTextWithShadow(textRenderer, duration > 0 ? formatTime(duration) : "--:--",
                barRight - 30, y + 62, 0xFFB0B8C5);
    }

    private void renderLyrics(DrawContext context, int y) {
        int left = panelLeft + 20;
        int maxWidth = panelWidth - 40;
        context.drawTextWithShadow(textRenderer, "SEKARANG", left, y + 10, 0xFF70C7D8);
        context.drawTextWithShadow(textRenderer, trim(LyricsFlowClient.getCurrentLyricPreview(), 48), left, y + 24, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "BERIKUTNYA", left, y + 43, 0xFF70C7D8);
        context.drawTextWithShadow(textRenderer, trim(LyricsFlowClient.getNextLyricPreview(), 48), left, y + 57, 0xFFB0B8C5);
        context.drawTextWithShadow(textRenderer, "Offset: " + LyricsFlowClient.getLyricOffsetMs() + " ms", left, y + 77, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "File: lyricsflow/current.lrc", left, y + 92, 0xFF8995A7);
    }

    private void renderPlaylist(DrawContext context, int y) {
        int x = panelLeft + 20;
        PlaylistStore.Playlist playlist = PlaylistStore.selected(selectedPlaylist);
        if (playlist == null) {
            context.drawTextWithShadow(textRenderer, "Belum ada playlist. Isi nama lalu tekan Buat.", x, y + 10, 0xFFFFFFFF);
            context.drawTextWithShadow(textRenderer, "Daftar tersimpan di lyricsflow/playlists.json", x, y + 28, 0xFFB0B8C5);
            return;
        }
        context.drawTextWithShadow(textRenderer, "Playlist: " + playlist.name + " (" + (selectedPlaylist + 1) + "/" + PlaylistStore.size() + ")", x, y + 8, 0xFFFFFFFF);
        if (playlist.tracks.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "Belum ada lagu. Isi judul lalu + Tambah lagu.", x, y + 26, 0xFFB0B8C5);
        } else {
            selectedTrack = Math.floorMod(selectedTrack, playlist.tracks.size());
            PlaylistStore.Track track = playlist.tracks.get(selectedTrack);
            context.drawTextWithShadow(textRenderer, "Lagu " + (selectedTrack + 1) + "/" + playlist.tracks.size() + ": " + trim(track.title, 36), x, y + 26, 0xFF70C7D8);
            context.drawTextWithShadow(textRenderer, track.lrcFile.isBlank() ? "LRC: otomatis berdasarkan judul" : "LRC: " + trim(track.lrcFile, 34), x, y + 40, 0xFF8995A7);
        }
        context.drawTextWithShadow(textRenderer, "Tambah: Judul | nama-file.lrc", x, y + 62, 0xFF8995A7);
        context.drawTextWithShadow(textRenderer, "File LRC berada di folder lyricsflow/", x, y + 76, 0xFF8995A7);
    }

    private static String formatTime(long milliseconds) {
        long totalSeconds = Math.max(0L, milliseconds) / 1000L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

    private static String trim(String value, int max) {
        return value.length() > max ? value.substring(0, max - 3) + "..." : value;
    }

    private void renderSpotify(DrawContext context, int y) {
        int x = panelLeft + 20;
        String connection = SpotifyService.connected() ? "Spotify: Terhubung" : "Spotify: Belum terhubung";
        context.drawTextWithShadow(textRenderer, connection, x, y + 66, SpotifyService.connected() ? 0xFF70C7D8 : 0xFFB0B8C5);
        context.drawTextWithShadow(textRenderer, trim(spotifySearchStatus, 58), x, y + 78, 0xFFB0B8C5);
        for (int i = 0; i < Math.min(4, spotifyResults.size()); i++) {
            SpotifyService.Track track = spotifyResults.get(i);
            context.drawTextWithShadow(textRenderer, trim(track.name + " — " + track.artist, 52), x, y + 94 + i * 14, 0xFFFFFFFF);
            context.drawTextWithShadow(textRenderer, trim(track.album, 52), x, y + 106 + i * 14, 0xFF8995A7);
        }
        if (!spotifyResults.isEmpty()) context.drawTextWithShadow(textRenderer, "Hasil hanya metadata; audio tidak diputar.", x, y + 154, 0xFF8995A7);
    }

    private void renderYouTube(DrawContext context, int y) {
        int x = panelLeft + 20;
        String status = !youtubeSearchStatus.isBlank()
                ? youtubeSearchStatus
                : (youtubeResults.isEmpty() ? "Cari video langsung dari menu ini." : "Hasil pencarian YouTube");
        if (!selectedYoutubeVideoId.isBlank()) {
            status = "Dipilih: " + selectedYoutubeVideoTitle;
        } else if (!youtubeResults.isEmpty()) {
            status += " (" + (youtubeScrollOffset + 1) + "-"
                    + Math.min(youtubeScrollOffset + 3, youtubeResults.size()) + "/" + youtubeResults.size()
                    + (youtubeNextPageToken.isBlank() ? "" : ", gulir untuk lanjut") + ")";
        }
        // Keep status below the search button and above the result rows.
        context.drawTextWithShadow(textRenderer, trim(status, 58), x, y + 66,
                selectedYoutubeVideoId.isBlank() ? 0xFFB0B8C5 : 0xFF70C7D8);
        if (!selectedYoutubeVideoId.isBlank()) {
            context.drawTextWithShadow(textRenderer, "ID: " + selectedYoutubeVideoId, x, y + 78, 0xFF8995A7);
        }
    }

    private void renderService(DrawContext context, int y, String name, String description) {
        if (name.equals("YouTube") || name.equals("Spotify")) {
            if (name.equals("Spotify")) {
                context.drawTextWithShadow(textRenderer, "Pencarian Spotify belum aktif.", panelLeft + 22, y + 76, 0xFFB0B8C5);
                context.drawTextWithShadow(textRenderer, "Perlu OAuth/login aman sebelum bisa mencari.", panelLeft + 22, y + 90, 0xFF8995A7);
                context.drawTextWithShadow(textRenderer, "Client ID saja tidak cukup; jangan masukkan Client Secret ke JAR.", panelLeft + 22, y + 104, 0xFF8995A7);
            } else {
                context.drawTextWithShadow(textRenderer, "Pencarian YouTube menampilkan metadata video.", panelLeft + 22, y + 76, 0xFFB0B8C5);
                context.drawTextWithShadow(textRenderer, "Memilih hasil tidak memutar audio di Minecraft.", panelLeft + 22, y + 90, 0xFF8995A7);
            }
        } else {
            context.drawTextWithShadow(textRenderer, description, panelLeft + 22, y + 18, 0xFFB0B8C5);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (selectedTab == Tab.YOUTUBE && youtubeResults.size() > 0
                && mouseX >= panelLeft + 12 && mouseX <= panelLeft + panelWidth - 12
                && mouseY >= panelTop + 250 && mouseY <= panelTop + panelHeight - 12) {
            int maxOffset = Math.max(0, youtubeResults.size() - 3);
            if (amount < 0 && youtubeScrollOffset >= maxOffset
                    && !youtubeNextPageToken.isBlank() && !youtubeSearching) {
                searchYouTube(youtubeLastQuery, youtubeNextPageToken, true);
                return true;
            }
            youtubeScrollOffset = Math.max(0, Math.min(maxOffset,
                    youtubeScrollOffset + (amount < 0 ? 1 : -1)));
            clearAndInit();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override public boolean shouldPause() { return false; }
}
