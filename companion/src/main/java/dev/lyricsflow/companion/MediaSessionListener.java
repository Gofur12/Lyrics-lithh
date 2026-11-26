package dev.lyricsflow.companion;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.content.ComponentName;
import android.os.SystemClock;

import java.io.IOException;
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
                    String body = snapshot();
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    OutputStream out = client.getOutputStream();
                    String headers = "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\n"
                            + "Cache-Control: no-store\r\nConnection: close\r\nContent-Length: " + bytes.length + "\r\n\r\n";
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
        String title = "";
        String artist = "";
        long position = 0L;
        boolean playing = false;
        boolean foundSession = false;
        try {
            ComponentName component = new ComponentName(this, MediaSessionListener.class);
            List<MediaController> sessions = getActiveSessions(component);
            for (MediaController controller : sessions) {
                PlaybackState state = controller.getPlaybackState();
                if (state == null) continue;
                int status = state.getState();
                if (status != PlaybackState.STATE_PLAYING && status != PlaybackState.STATE_PAUSED
                        && status != PlaybackState.STATE_BUFFERING) continue;
                MediaMetadata metadata = controller.getMetadata();
                if (metadata != null) {
                    title = value(metadata.getString(MediaMetadata.METADATA_KEY_TITLE));
                    artist = value(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST));
                }
                position = Math.max(0L, state.getPosition());
                if (status == PlaybackState.STATE_PLAYING) {
                    long updated = state.getLastPositionUpdateTime();
                    if (updated > 0) position += Math.max(0L, SystemClock.elapsedRealtime() - updated);
                }
                playing = status == PlaybackState.STATE_PLAYING;
                foundSession = true;
                break;
            }
        } catch (SecurityException ignored) { }

        return "title=" + enc(title) + "\nartist=" + enc(artist) + "\nposition_ms=" + position
                + "\nplaying=" + playing + "\nupdated_at_ms=" + System.currentTimeMillis() + "\n";
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
