package dev.lyricsflow;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Lightweight configuration screen exposed through Fabric Mod Menu. */
public final class LyricsFlowConfigScreen extends Screen {
    private final Screen parent;

    public LyricsFlowConfigScreen(Screen parent) {
        super(Text.literal("LyricsFlow Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int top = height / 2 - 82;
        int buttonWidth = Math.min(220, width - 32);
        int left = center - buttonWidth / 2;

        addDrawableChild(ButtonWidget.builder(hudLabel(), button -> {
            LyricsFlowClient.toggleOverlay();
            button.setMessage(hudLabel());
        }).dimensions(left, top, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Offset -100 ms"), button -> {
            LyricsFlowClient.adjustLyricOffset(-100L);
            clearAndInit();
        }).dimensions(left, top + 26, buttonWidth / 2 - 2, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Offset +100 ms"), button -> {
            LyricsFlowClient.adjustLyricOffset(100L);
            clearAndInit();
        }).dimensions(center + 2, top + 26, buttonWidth / 2 - 2, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset offset"), button -> {
            LyricsFlowClient.resetLyricOffset();
            clearAndInit();
        }).dimensions(left, top + 52, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("HUD naik 5 px"), button -> {
            LyricsFlowClient.adjustHudYOffset(5);
            clearAndInit();
        }).dimensions(left, top + 78, buttonWidth / 2 - 2, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("HUD turun 5 px"), button -> {
            LyricsFlowClient.adjustHudYOffset(-5);
            clearAndInit();
        }).dimensions(center + 2, top + 78, buttonWidth / 2 - 2, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Selesai"), button -> close())
                .dimensions(left, top + 108, buttonWidth, 20).build());
    }

    private Text hudLabel() {
        return Text.literal("HUD lirik: " + (LyricsFlowClient.isVisible() ? "ON" : "OFF"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 88, 0xFF70C7D8);
        context.drawCenteredTextWithShadow(textRenderer,
                "Offset: " + LyricsFlowClient.getLyricOffsetMs() + " ms | Posisi HUD: " + LyricsFlowClient.getHudYOffset() + " px",
                width / 2, height / 2 + 66, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
