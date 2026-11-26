package dev.lyricsflow;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Main LyricsFlow player shell. The layout is deliberately drawn with
 * vanilla GUI primitives so it works without a browser/native dependency.
 */
public final class LyricsFlowScreen extends Screen {
    private enum Tab {
        PLAYER("Player"), LYRICS("Lirik"), SPOTIFY("Spotify"),
        YOUTUBE("YouTube"), LOCAL("Musik HP"), PLAYLIST("Playlist"), APP("App");

        final String label;
        Tab(String label) { this.label = label; }
    }

    private Tab selectedTab = Tab.PLAYER;
    private int panelLeft, panelTop, panelWidth, panelHeight;

    public LyricsFlowScreen() {
        super(Text.literal("LyricsFlow"));
    }

    @Override
    protected void init() {
        clearChildren();
        panelWidth = Math.min(430, Math.max(260, width - 24));
        panelHeight = Math.min(230, Math.max(170, height - 36));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;

        int tabY = panelTop + 8;
        int tabX = panelLeft + 10;
        int tabW = Math.max(42, (panelWidth - 20) / Tab.values().length);
        for (Tab tab : Tab.values()) {
            final Tab target = tab;
            addDrawableChild(ButtonWidget.builder(Text.literal(tab.label), button -> {
                selectedTab = target;
                clearAndInit();
            }).dimensions(tabX, tabY, tabW - 2, 20).build());
            tabX += tabW;
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Ⅱ"), button -> {
            if (LyricsFlowClient.isPlaying()) LyricsFlowClient.pausePlayback();
            else LyricsFlowClient.startPlayback();
            clearAndInit();
        }).dimensions(panelLeft + panelWidth / 2 - 12, panelTop + panelHeight - 34, 24, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Lirik"), button -> {
            selectedTab = Tab.LYRICS;
            clearAndInit();
        }).dimensions(panelLeft + 10, panelTop + panelHeight - 34, 42, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Selesai"), button -> close())
                .dimensions(panelLeft + panelWidth - 58, panelTop + panelHeight - 34, 48, 20).build());
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
            case SPOTIFY -> renderService(context, contentTop, "Spotify", "Login dan katalog akan diintegrasikan pada tahap layanan.");
            case YOUTUBE -> renderService(context, contentTop, "YouTube", "youtube.com — bukan YouTube Music.");
            case LOCAL -> renderService(context, contentTop, "Musik HP", "Akses file musik Android memerlukan integrasi penyimpanan perangkat.");
            case APP -> renderService(context, contentTop, "App", "Pengaturan dan integrasi LyricsFlow.");
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPlayer(DrawContext context, int y) {
        int x = panelLeft + 22;
        context.fill(x, y + 8, x + 76, y + 84, 0xFF394252);
        context.drawCenteredTextWithShadow(textRenderer, "♪", x + 38, y + 38, 0xFFB9DDE5);
        context.drawTextWithShadow(textRenderer, "Belum ada lagu", x + 90, y + 12, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "Pilih sumber musik atau playlist", x + 90, y + 27, 0xFFB0B8C5);
        context.fill(x + 90, y + 52, panelLeft + panelWidth - 22, y + 55, 0xFF596273);
        context.drawTextWithShadow(textRenderer, "00:00", x + 90, y + 62, 0xFFB0B8C5);
        context.drawTextWithShadow(textRenderer, "--:--", panelLeft + panelWidth - 52, y + 62, 0xFFB0B8C5);
        context.drawTextWithShadow(textRenderer, "PREV       PLAY / PAUSE       NEXT", x + 90, y + 86, 0xFF70C7D8);
    }

    private void renderLyrics(DrawContext context, int y) {
        context.drawCenteredTextWithShadow(textRenderer, "Lirik tersinkronisasi LRC", panelLeft + panelWidth / 2, y + 24, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, "Letakkan file .lrc di folder lyricsflow", panelLeft + panelWidth / 2, y + 44, 0xFFB0B8C5);
        context.drawCenteredTextWithShadow(textRenderer, "Offset: " + LyricsFlowClient.getLyricOffsetMs() + " ms", panelLeft + panelWidth / 2, y + 64, 0xFF70C7D8);
    }

    private void renderPlaylist(DrawContext context, int y) {
        context.drawTextWithShadow(textRenderer, "PLAYLIST LOKAL", panelLeft + 22, y + 14, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "Belum ada playlist.", panelLeft + 22, y + 34, 0xFFB0B8C5);
        context.drawTextWithShadow(textRenderer, "Manajemen playlist: tahap berikutnya.", panelLeft + 22, y + 54, 0xFF70C7D8);
    }

    private void renderService(DrawContext context, int y, String name, String description) {
        context.drawTextWithShadow(textRenderer, name, panelLeft + 22, y + 18, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, description, panelLeft + 22, y + 38, 0xFFB0B8C5);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
