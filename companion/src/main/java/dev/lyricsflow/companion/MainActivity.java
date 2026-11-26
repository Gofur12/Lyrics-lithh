package dev.lyricsflow.companion;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
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
        info.setText("Enable notification access so LyricsFlow can read the active music session. The local bridge listens only on 127.0.0.1:8765.");
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
        status.setText("Connection not tested yet.");
        status.setTextSize(14);
        status.setPadding(0, 16, 0, 16);
        layout.addView(status);

        TextView steps = new TextView(this);
        steps.setText("After enabling access, return to this app and tap Test music connection. Start playing music first. Then launch Minecraft. Keep this app installed; no account login is needed.");
        steps.setTextSize(14);
        layout.addView(steps);

        setContentView(layout);
    }

    private void testBridge() {
        status.setText("Checking local music session...");
        checker.execute(() -> {
            String result;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL("http://127.0.0.1:8765/nowplaying").openConnection();
                connection.setConnectTimeout(1000);
                connection.setReadTimeout(1000);
                connection.setUseCaches(false);
                if (connection.getResponseCode() != 200) {
                    result = "Bridge responded, but the endpoint is unavailable. Reopen the app or re-enable Notification Access.";
                } else {
                    String title = "";
                    String artist = "";
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("title=")) title = decode(line.substring(6));
                            else if (line.startsWith("artist=")) artist = decode(line.substring(7));
                        }
                    }
                    if (title.isEmpty()) {
                        result = "Bridge is running, but no active music session was found. Start playback in a media app that exposes Android MediaSession.";
                    } else {
                        result = "Connected: " + title + (artist.isEmpty() ? "" : " — " + artist);
                    }
                }
            } catch (Exception exception) {
                result = "Not connected. Enable Notification Access, start music, then reopen this app. (" 
                        + exception.getClass().getSimpleName() + ")";
            } finally {
                if (connection != null) connection.disconnect();
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
