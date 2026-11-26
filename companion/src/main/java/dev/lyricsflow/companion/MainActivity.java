package dev.lyricsflow.companion;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService checker = Executors.newSingleThreadExecutor();
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 36, 28, 28);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("LyricsFlow Companion");
        title.setTextSize(24);
        layout.addView(title);

        TextView info = new TextView(this);
        info.setText("1. Enable LyricsFlow Media Session Access.\\n2. Play music in Spotify or YouTube.\\n3. Return here and tap Test music connection.\\nLocal bridge: 127.0.0.1:8765");
        info.setTextSize(16);
        info.setPadding(0, 20, 0, 20);
        layout.addView(info);

        Button settings = new Button(this);
        settings.setText("Open notification access settings");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        layout.addView(settings);

        Button test = new Button(this);
        test.setText("Test music connection");
        test.setOnClickListener(v -> testBridge());
        layout.addView(test);

        status = new TextView(this);
        status.setText("Ready. Start music, then test the connection.");
        status.setTextSize(14);
        status.setPadding(0, 16, 0, 16);
        layout.addView(status);

        setContentView(layout);
    }

    private void testBridge() {
        status.setText("Reconnecting to Android media access...");
        checker.execute(() -> {
            String result = "";
            try {
                // Ask Android to rebind the notification listener after access was granted.
                NotificationListenerService.requestRebind(
                        new ComponentName(this, MediaSessionListener.class));

                for (int attempt = 1; attempt <= 10; attempt++) {
                    HttpURLConnection connection = null;
                    try {
                        connection = (HttpURLConnection) new URL(
                                "http://127.0.0.1:8765/nowplaying").openConnection();
                        connection.setConnectTimeout(700);
                        connection.setReadTimeout(1000);
                        connection.setUseCaches(false);
                        if (connection.getResponseCode() == 200) {
                            String title = "";
                            String artist = "";
                            String playing = "false";
                            try (BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    int split = line.indexOf('=');
                                    if (split < 0) continue;
                                    String key = line.substring(0, split);
                                    String value = line.substring(split + 1);
                                    if ("title".equals(key)) title = decode(value);
                                    else if ("artist".equals(key)) artist = decode(value);
                                    else if ("playing".equals(key)) playing = value;
                                }
                            }
                            if (!title.isEmpty()) {
                                result = "Connected: " + title
                                        + (artist.isEmpty() ? "" : " — " + artist)
                                        + (Boolean.parseBoolean(playing) ? "\\nPlayback: PLAYING" : "\\nPlayback: PAUSED");
                            } else {
                                result = "Bridge is running, but Android did not expose an active song. Keep Spotify/YouTube playing and try again.";
                            }
                            break;
                        } else {
                            result = "Local bridge responded with HTTP " + connection.getResponseCode() + ".";
                        }
                    } catch (ConnectException exception) {
                        result = "Local bridge is not running yet. Android has been asked to reconnect the media listener. Check Notification Access is ON, then try again.";
                    } catch (Exception exception) {
                        result = "Connection error: " + exception.getClass().getSimpleName()
                                + (exception.getMessage() == null ? "" : " — " + exception.getMessage());
                    } finally {
                        if (connection != null) connection.disconnect();
                    }
                    if (result.startsWith("Connected:") || result.startsWith("Bridge is running")
                            || result.startsWith("Connection error:")) break;
                    if (attempt < 10) {
                        try { Thread.sleep(500); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            result = "Connection test cancelled.";
                            break;
                        }
                    }
                }
            } catch (Exception exception) {
                result = "Unable to request Android media listener: " + exception.getClass().getSimpleName();
            }
            final String message = result;
            runOnUiThread(() -> status.setText(message));
        });
    }

    private static String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (Exception exception) {
            return value;
        }
    }

    @Override
    protected void onDestroy() {
        checker.shutdownNow();
        super.onDestroy();
    }
}
