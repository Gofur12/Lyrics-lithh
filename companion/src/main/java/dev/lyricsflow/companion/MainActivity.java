package dev.lyricsflow.companion;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
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

        TextView steps = new TextView(this);
        steps.setText("\nAfter enabling access, return to this app. The media-session listener starts the local bridge automatically. Keep this app installed; no account login is needed.");
        steps.setTextSize(14);
        layout.addView(steps);

        setContentView(layout);
    }
}
