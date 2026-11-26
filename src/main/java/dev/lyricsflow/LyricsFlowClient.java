package dev.lyricsflow;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import com.mojang.brigadier.arguments.LongArgumentType;
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
    private static final Path SETTINGS_FILE = Path.of("lyricsflow", "settings.properties");
    private static volatile long lyricOffsetMs = 0L;
    private static volatile NowPlayingBridge.State externalState;
    private static String lastExternalTitle = "";
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
    private static String selectedLocalTitle = "";
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
        loadSettings();
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
            long rawPosition = externalState != null ? externalPositionMs(externalState) : positionMs();
            long position = Math.max(0L, rawPosition - lyricOffsetMs);
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
                        }))
                        .then(literal("offset")
                                .then(argument("milliseconds", LongArgumentType.longArg(-60000L, 60000L))
                                        .executes(context -> {
                                            setLyricOffset(LongArgumentType.getLong(context, "milliseconds"));
                                            context.getSource().sendFeedback(Text.literal(
                                                    "LyricsFlow offset: " + lyricOffsetMs + " ms"));
                                            return 1;
                                        }))
                                .then(literal("reset").executes(context -> {
                                    setLyricOffset(0L);
                                    context.getSource().sendFeedback(Text.literal("LyricsFlow offset reset to 0 ms"));
                                    return 1;
                                })))
                        .then(literal("menu").executes(context -> {
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
    static String getSelectedLocalTitle() { return selectedLocalTitle; }
    static long getPlaybackPositionMs() {
        NowPlayingBridge.State state = externalState;
        return Math.max(0L, state != null ? externalPositionMs(state) : positionMs());
    }
    static long getLyricsDurationMs() { return track.durationMs(); }
    static String getLrcTitle() { return track.title(); }
    static String getLrcArtist() { return track.artist(); }
    static String getLrcAlbum() { return track.album(); }
    static boolean hasExternalPlayer() { return externalState != null; }
    static void selectLocalTrack(String title) {
        selectedLocalTitle = title == null ? "" : title.trim();
        pausedAt = 0L;
        playing = false;
        loadTrackForTitle(selectedLocalTitle);
    }
    static void toggleOverlay() { visible = !visible; }
    static void startPlayback() { if (!playing) togglePlayback(); }
    static void pausePlayback() { if (playing) togglePlayback(); }
    static void reloadLyrics() { loadTrack(); }
    static long getLyricOffsetMs() { return lyricOffsetMs; }
    static String getCurrentLyricPreview() {
        long raw = externalState != null ? externalPositionMs(externalState) : positionMs();
        LrcTrack.Entry entry = track.current(Math.max(0L, raw - lyricOffsetMs));
        return entry == null ? "—" : entry.text();
    }
    static String getNextLyricPreview() {
        long raw = externalState != null ? externalPositionMs(externalState) : positionMs();
        LrcTrack.Entry entry = track.next(Math.max(0L, raw - lyricOffsetMs));
        return entry == null ? "—" : entry.text();
    }
    static void adjustLyricOffset(long amountMs) {
        setLyricOffset(Math.max(-60000L, Math.min(60000L, lyricOffsetMs + amountMs)));
    }
    static void resetLyricOffset() { setLyricOffset(0L); }

    private static void setLyricOffset(long offsetMs) {
        lyricOffsetMs = Math.max(-60000L, Math.min(60000L, offsetMs));
        saveSettings();
    }

    private static void loadSettings() {
        MinecraftClient client = MinecraftClient.getInstance();
        Path file = client.runDirectory.toPath().resolve(SETTINGS_FILE);
        if (!Files.isRegularFile(file)) return;
        java.util.Properties properties = new java.util.Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            lyricOffsetMs = Math.max(-60000L, Math.min(60000L,
                    Long.parseLong(properties.getProperty("offset_ms", "0").trim())));
        } catch (IOException | NumberFormatException exception) {
            lyricOffsetMs = 0L;
        }
    }

    private static void saveSettings() {
        MinecraftClient client = MinecraftClient.getInstance();
        Path file = client.runDirectory.toPath().resolve(SETTINGS_FILE);
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("offset_ms", Long.toString(lyricOffsetMs));
        try {
            Files.createDirectories(file.getParent());
            try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                properties.store(writer, "LyricsFlow settings");
            }
        } catch (IOException exception) {
            client.execute(() -> client.inGameHud.setOverlayMessage(
                    Text.literal("LyricsFlow: cannot save settings").formatted(Formatting.RED), false));
        }
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

    private static long externalPositionMs(NowPlayingBridge.State state) {
        long elapsed = state.playing() ? Math.max(0L, System.currentTimeMillis() - state.sampledAtMs()) : 0L;
        return Math.max(0L, state.positionMs() + elapsed);
    }

    private static void loadTrack() {
        NowPlayingBridge.State state = externalState;
        loadTrackForTitle(state == null ? "" : state.title());
    }

    private static void loadTrackForTitle(String title) {
        if (title != null && !title.isBlank()) selectedLocalTitle = title.trim();
        MinecraftClient client = MinecraftClient.getInstance();
        Path gameDir = client.runDirectory.toPath();
        Path fallback = gameDir.resolve(LYRICS_FILE);
        String safeTitle = sanitizeFileName(title);
        Path songFile = safeTitle.isEmpty() ? fallback
                : gameDir.resolve("lyricsflow").resolve(safeTitle + ".lrc");
        Path file = Files.isRegularFile(songFile) ? songFile : fallback;
        try {
            Files.createDirectories(fallback.getParent());
            if (!Files.exists(fallback)) Files.writeString(fallback,
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
                    Text.literal("LyricsFlow: cannot read " + file.getFileName()).formatted(Formatting.RED), false));
        }
    }

    private static String sanitizeFileName(String title) {
        if (title == null || title.isBlank()) return "";
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < title.length(); i++) {
            char ch = title.charAt(i);
            safe.append("/\\:*?\"<>|".indexOf(ch) >= 0 ? '_' : ch);
        }
        String result = safe.toString().trim();
        while (result.endsWith(".") || result.endsWith(" ")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.length() > 80) result = result.substring(0, 80).trim();
        return result;
    }
}
