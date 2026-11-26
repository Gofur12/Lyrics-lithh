package dev.lyricsflow.companion;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.content.ComponentName;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MediaSessionListener extends NotificationListenerService {
    private volatile ServerSocket server;
    private ExecutorService worker;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        if (worker != null && !worker.isShutdown()) return;
        worker = Executors.newSingleThreadExecutor();
        worker.execute(this::serve);
    }

    @Override
    public void onListenerDisconnected() {
        stopServer();
        super.onListenerDisconnected();
    }

    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    private void serve() {
        try (ServerSocket socket = new ServerSocket(8765, 8, InetAddress.getByName("127.0.0.1"))) {
            server = socket;
            while (!socket.isClosed()) {
                try (Socket client = socket.accept()) {
                    client.setSoTimeout(1500);
                    BufferedReader request = new BufferedReader(
                            new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                    String requestLine = request.readLine();
                    String path = "";
                    if (requestLine != null) {
                        String[] parts = requestLine.split(" ", 3);
                        if (parts.length >= 2) path = parts[1];
                    }
                    String body = "/nowplaying".equals(path) ? snapshot() : "Not Found\\n";
                    int status = "/nowplaying".equals(path) ? 200 : 404;
                    String reason = status == 200 ? "OK" : "Not Found";
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    OutputStream out = client.getOutputStream();
                    String headers = "HTTP/1.1 " + status + " " + reason + "\\r\\n"
                            + "Content-Type: text/plain; charset=utf-8\\r\\n"
                            + "Cache-Control: no-store\\r\\nConnection: close\\r\\nContent-Length: "
                            + bytes.length + "\\r\\n\\r\\n";
                    out.write(headers.getBytes(StandardCharsets.US_ASCII));
                    out.write(bytes);
                    out.flush();
                } catch (IOException ignored) { }
            }
        } catch (IOException ignored) {
            // Port unavailable or listener was disabled; app can be reopened to retry.
        } finally {
            server = null;
        }
    }

    private String snapshot() {
        MediaController selected = null;
        PlaybackState selectedState = null;
        int selectedRank = -1;
        try {
            ComponentName component = new ComponentName(this, MediaSessionListener.class);
            List<MediaController> sessions = getActiveSessions(component);
            for (MediaController controller : sessions) {
                PlaybackState state = controller.getPlaybackState();
                if (state == null) continue;
                int status = state.getState();
                int rank;
                if (status == PlaybackState.STATE_PLAYING) rank = 3;
                else if (status == PlaybackState.STATE_BUFFERING) rank = 2;
                else if (status == PlaybackState.STATE_PAUSED) rank = 1;
                else continue;

                MediaMetadata metadata = controller.getMetadata();
                boolean hasTitle = metadata != null
                        && metadata.getString(MediaMetadata.METADATA_KEY_TITLE) != null
                        && !metadata.getString(MediaMetadata.METADATA_KEY_TITLE).trim().isEmpty();
                if (hasTitle) rank++;
                if (rank > selectedRank) {
                    selected = controller;
                    selectedState = state;
                    selectedRank = rank;
                }
            }
        } catch (SecurityException ignored) { }

        if (selected == null || selectedState == null) {
            return "title=\\nartist=\\nposition_ms=0\\nplaying=false\\nupdated_at_ms=0\\n";
        }

        MediaMetadata metadata = selected.getMetadata();
        String title = metadata == null ? "" : value(metadata.getString(MediaMetadata.METADATA_KEY_TITLE));
        String artist = metadata == null ? "" : value(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST));
        int status = selectedState.getState();
        long position = Math.max(0L, selectedState.getPosition());
        if (status == PlaybackState.STATE_PLAYING) {
            long updated = selectedState.getLastPositionUpdateTime();
            if (updated > 0) position += Math.max(0L, SystemClock.elapsedRealtime() - updated);
        }
        boolean playing = status == PlaybackState.STATE_PLAYING;
        return "title=" + enc(title) + "\\nartist=" + enc(artist) + "\\nposition_ms=" + position
                + "\\nplaying=" + playing + "\\nupdated_at_ms=" + System.currentTimeMillis() + "\\n";
    }

    private static String value(String text) { return text == null ? "" : text; }
    private static String enc(String text) {
        try { return URLEncoder.encode(text, "UTF-8"); }
        catch (Exception ignored) { return ""; }
    }

    private void stopServer() {
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        if (worker != null) worker.shutdownNow();
        worker = null;
    }
}
