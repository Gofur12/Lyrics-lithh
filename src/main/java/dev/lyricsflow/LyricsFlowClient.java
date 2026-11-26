package dev.lyricsflow;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LyricsFlowClient implements ClientModInitializer {
    private static final Path LYRICS_FILE = Path.of("lyricsflow", "current.lrc");
    private static LrcTrack track = LrcTrack.parse("");
    private static boolean visible = true;
    private static boolean playing = false;
    private static long startedAt = 0L;
    private static long pausedAt = 0L;
    private static KeyBinding toggleKey, playPauseKey, reloadKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lyricsflow.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.lyricsflow"));
        playPauseKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lyricsflow.play_pause", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "category.lyricsflow"));
        reloadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lyricsflow.reload", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, "category.lyricsflow"));
        loadTrack();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) visible = !visible;
            while (playPauseKey.wasPressed()) togglePlayback();
            while (reloadKey.wasPressed()) loadTrack();
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!visible || track.isEmpty()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.options.hudHidden) return;
            long position = positionMs();
            LrcTrack.Entry current = track.current(position);
            LrcTrack.Entry next = track.next(position);
            int centerX = client.getWindow().getScaledWidth() / 2;
            int y = client.getWindow().getScaledHeight() - 58;
            String currentText = current == null ? "♪ LyricsFlow ready" : current.text();
            String nextText = next == null ? "" : next.text();
            drawCentered(drawContext, client, (playing ? "▶" : "Ⅱ") + " LyricsFlow", centerX, y - 13, 0x80D8FF);
            drawCentered(drawContext, client, currentText, centerX, y, 0xFFFFFF);
            if (!nextText.isEmpty()) drawCentered(drawContext, client, nextText, centerX, y + 13, 0xB8B8B8);
        });
    }

    private static void drawCentered(net.minecraft.client.gui.DrawContext context,
                                     MinecraftClient client, String text, int x, int y, int color) {
        String safe = text.length() > 100 ? text.substring(0, 97) + "..." : text;
        int width = client.textRenderer.getWidth(safe);
        context.fill(x - width / 2 - 5, y - 2, x + width / 2 + 5, y + 11, 0x90000000);
        context.drawTextWithShadow(client.textRenderer, Text.literal(safe), x - width / 2, y, color);
    }

    private static void togglePlayback() {
        long now = System.currentTimeMillis();
        if (playing) {
            pausedAt = Math.max(0, now - startedAt);
            playing = false;
        } else {
            startedAt = now - pausedAt;
            playing = true;
        }
    }

    private static long positionMs() {
        return playing ? Math.max(0, System.currentTimeMillis() - startedAt) : pausedAt;
    }

    private static void loadTrack() {
        MinecraftClient client = MinecraftClient.getInstance();
        Path file = client.runDirectory.toPath().resolve(LYRICS_FILE);
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) Files.writeString(file,
                    "[00:00.00]Put your synced lyrics here\n[00:05.00]Press P to start or pause\n",
                    StandardCharsets.UTF_8);
            track = LrcTrack.parse(Files.readString(file, StandardCharsets.UTF_8));
            pausedAt = 0;
            playing = false;
            client.execute(() -> client.inGameHud.setOverlayMessage(
                    Text.literal("LyricsFlow: LRC loaded").formatted(Formatting.AQUA), false));
        } catch (IOException exception) {
            track = LrcTrack.parse("");
            client.execute(() -> client.inGameHud.setOverlayMessage(
                    Text.literal("LyricsFlow: cannot read current.lrc").formatted(Formatting.RED), false));
        }
    }
}
