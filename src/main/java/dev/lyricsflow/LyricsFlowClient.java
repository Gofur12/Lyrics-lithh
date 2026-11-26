package dev.lyricsflow;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LyricsFlowClient implements ClientModInitializer {
    private static final Path LYRICS_FILE = Path.of("lyricsflow", "current.lrc");
    private static final Path NOW_PLAYING_FILE = Path.of("lyricsflow", "nowplaying.properties");
    private static volatile NowPlayingBridge.State externalState;
    private static String lastExternalTitle = "";
    private static Path loadedLyricsPath;
    private static int bridgePollTicks = 0;
    private static final AtomicBoolean bridgeRequestInFlight = new AtomicBoolean(false);
    private static final ExecutorService bridgeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "LyricsFlow-Bridge");
        thread.setDaemon(true);
        return thread;
    });
    private static LrcTrack track = LrcTrack.parse("");
    private static boolean visible = true;
    private static boolean playing = false;
    private static long startedAt = 0L;
    private static long pausedAt = 0L;
    private static KeyBinding toggleKey, playPauseKey, reloadKey, menuKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lyricsflow.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.lyricsflow"));
        playPauseKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lyricsflow.play_pause", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "category.lyricsflow"));
        reloadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lyricsflow.reload", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, "category.lyricsflow"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lyricsflow.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.lyricsflow"));
        loadTrack();
        registerCommands();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++bridgePollTicks >= 10) {
                bridgePollTicks = 0;
                if (bridgeRequestInFlight.compareAndSet(false, true)) {
                    bridgeExecutor.execute(() -> {
                        try {
                            externalState = NowPlayingBridge.read(client.runDirectory.toPath().resolve(NOW_PLAYING_FILE));
                        } finally {
                            bridgeRequestInFlight.set(false);
                        }
                    });
                }
            }
            NowPlayingBridge.State state = externalState;
            if (state != null && !state.title().isBlank() && !state.title().equals(lastExternalTitle)) {
                lastExternalTitle = state.title();
                loadTrackForTitle(state.title());
            }
            while (toggleKey.wasPressed()) visible = !visible;
            while (playPauseKey.wasPressed()) togglePlayback();
            while (reloadKey.wasPressed()) loadTrack();
            while (menuKey.wasPressed()) client.setScreen(new LyricsFlowScreen());
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!visible || track.isEmpty()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.options.hudHidden) return;
            long position = externalState != null ? externalState.positionMs() : positionMs();
            LrcTrack.Entry current = track.current(position);
            LrcTrack.Entry next = track.next(position);
            int centerX = client.getWindow().getScaledWidth() / 2;
            int y = client.getWindow().getScaledHeight() - 58;
            String currentText = current == null ? "♪ LyricsFlow ready" : current.text();
            String nextText = next == null ? "" : next.text();
            boolean externalPlaying = externalState != null ? externalState.playing() : playing;
            String sourceTitle = externalState == null ? "LyricsFlow" :
                    (externalState.title().isBlank() ? "External player" : externalState.title());
            drawCentered(drawContext, client, (externalPlaying ? "▶ " : "Ⅱ ") + sourceTitle, centerX, y - 26, 0x80D8FF);
            if (externalState != null && !externalState.artist().isBlank()) {
                drawCentered(drawContext, client, externalState.artist(), centerX, y - 13, 0xC8C8C8);
            }
            drawCentered(drawContext, client, currentText, centerX, y, 0xFFFFFF);
            if (!nextText.isEmpty()) drawCentered(drawContext, client, nextText, centerX, y + 13, 0xB8B8B8);
        });
    }


    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("lyricsflow")
                        .then(literal("reload").executes(context -> {
                            loadTrack();
                            context.getSource().sendFeedback(Text.literal("LyricsFlow: LRC reloaded"));
                            return 1;
                        }))
                        .then(literal("toggle").executes(context -> {
                            visible = !visible;
                            context.getSource().sendFeedback(Text.literal("LyricsFlow overlay: " + (visible ? "shown" : "hidden")));
                            return 1;
                        }))
                        .then(literal("play").executes(context -> {
                            if (!playing) togglePlayback();
                            context.getSource().sendFeedback(Text.literal("LyricsFlow timeline started"));
                            return 1;
                        }))
                        .then(literal("pause").executes(context -> {
                            if (playing) togglePlayback();
                            context.getSource().sendFeedback(Text.literal("LyricsFlow timeline paused"));
                            return 1;
                        })).then(literal("menu").executes(context -> {
                            context.getSource().getClient().setScreen(new LyricsFlowScreen());
                            return 1;
                        }))));
    }

    private static void drawCentered(net.minecraft.client.gui.DrawContext context,
                                     MinecraftClient client, String text, int x, int y, int color) {
        String safe = text.length() > 100 ? text.substring(0, 97) + "..." : text;
        int width = client.textRenderer.getWidth(safe);
        context.fill(x - width / 2 - 5, y - 2, x + width / 2 + 5, y + 11, 0x90000000);
        context.drawTextWithShadow(client.textRenderer, Text.literal(safe), x - width / 2, y, color);
    }

    static boolean isVisible() { return visible; }
    static boolean isPlaying() { return playing; }
    static void toggleOverlay() { visible = !visible; }
    static void startPlayback() { if (!playing) togglePlayback(); }
    static void pausePlayback() { if (playing) togglePlayback(); }
    static void reloadLyrics() { loadTrack(); }

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
                    Text.literal("LyricsFlow: loaded " + file.getFileName()).formatted(Formatting.AQUA), false));
        } catch (IOException exception) {
            track = LrcTrack.parse("");
            client.execute(() -> client.inGameHud.setOverlayMessage(
                    Text.literal("LyricsFlow: cannot read current.lrc").formatted(Formatting.RED), false));
        }
    }
}
