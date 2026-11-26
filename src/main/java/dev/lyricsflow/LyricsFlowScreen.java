package dev.lyricsflow;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Simple touch-friendly control screen. */
public final class LyricsFlowScreen extends Screen {
    public LyricsFlowScreen() {
        super(Text.literal("LyricsFlow"));
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(240, this.width - 40);
        int left = (this.width - buttonWidth) / 2;
        int top = this.height / 4;

        addDrawableChild(ButtonWidget.builder(
                Text.literal(LyricsFlowClient.isVisible() ? "Hide lyrics overlay" : "Show lyrics overlay"),
                button -> {
                    LyricsFlowClient.toggleOverlay();
                    this.clearAndInit();
                }).dimensions(left, top, buttonWidth, 24).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal(LyricsFlowClient.isPlaying() ? "Pause lyric timeline" : "Start lyric timeline"),
                button -> {
                    if (LyricsFlowClient.isPlaying()) LyricsFlowClient.pausePlayback();
                    else LyricsFlowClient.startPlayback();
                    this.clearAndInit();
                }).dimensions(left, top + 32, buttonWidth, 24).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Reload current.lrc"),
                button -> LyricsFlowClient.reloadLyrics()).dimensions(left, top + 64, buttonWidth, 24).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Done"),
                button -> close()).dimensions(left, top + 104, buttonWidth, 24).build());
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 4 - 24, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Touch controls for your synced LRC file"),
                width / 2, height / 4 - 10, 0xAAAAAA);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
