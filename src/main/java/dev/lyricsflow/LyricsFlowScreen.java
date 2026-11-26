package dev.lyricsflow;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

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

    public LyricsFlowScreen() { super(Text.literal("LyricsFlow")); }

    @Override
    protected void init() {
        clearChildren();
        panelWidth = Math.min(430, Math.max(260, width - 24));
        panelHeight = Math.min(230, Math.max(170, height - 36));
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
        } else {
            if (selectedTab == Tab.PLAYER) {
                addDrawableChild(ButtonWidget.builder(Text.literal("Ⅱ / ▶"), button -> {
                    if (LyricsFlowClient.isPlaying()) LyricsFlowClient.pausePlayback();
                    else LyricsFlowClient.startPlayback();
                    clearAndInit();
                }).dimensions(panelLeft + panelWidth / 2 - 68, panelTop + panelHeight - 34, 64, 20).build());
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
            }
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Selesai"), button -> close())
                .dimensions(panelLeft + panelWidth - 58, panelTop + panelHeight - 34, 48, 20).build());
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
            case SPOTIFY -> renderService(context, contentTop, "Spotify", "Integrasi login dan pemutaran belum tersedia.");
            case YOUTUBE -> renderService(context, contentTop, "YouTube", "youtube.com — bukan YouTube Music.");
            case LOCAL -> renderService(context, contentTop, "Musik HP", "Akses audio Android belum terhubung.");
            case APP -> renderService(context, contentTop, "App", "Pengaturan LyricsFlow.");
        }
        super.render(context, mouseX, mouseY, delta);
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

    private void renderService(DrawContext context, int y, String name, String description) {
        context.drawTextWithShadow(textRenderer, name, panelLeft + 22, y + 18, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, description, panelLeft + 22, y + 38, 0xFFB0B8C5);
    }

    @Override public boolean shouldPause() { return false; }
}
