# LyricsFlow Android — Fabric 1.20.1

Lightweight client-side LRC lyric overlay for Minecraft Java 1.20.1, intended for Android Java launchers such as Zalith Launcher 2.

## Status

**Early prototype: local LRC plus an Android MediaSession companion.** The repository now includes a separate Android companion APK that requests notification-listener access, reads active media sessions, and exposes the current track to the mod through a loopback-only HTTP endpoint (`127.0.0.1:8765`). The companion does not access YouTube Music private APIs or Google OAuth; it reads only Android's active media-session metadata when the user grants access. End-to-end operation on Zalith still needs device testing.

## Build

- Minecraft 1.20.1
- Fabric Loader 0.15.11
- Fabric API 0.92.2+1.20.1
- Java 17

GitHub Actions builds both the Fabric JAR and Android companion debug APK on pushes and pull requests. Download `lyricsflow-fabric-1.20.1` and `lyricsflow-android-companion-debug` from a successful workflow run's Artifacts section.

## Install and use

1. Build the project or download the JAR artifact from GitHub Actions.
2. Put the JAR in the instance's mods folder.
3. Launch Minecraft 1.20.1 with Fabric.
4. On first launch the mod creates a lyricsflow/current.lrc file in the game directory.
5. Replace that file with an LRC lyric file.

| Key | Action |
| --- | --- |
| O | Show or hide lyrics |
| P | Start or pause lyric timeline |
| L | Reload current.lrc |
| H | Open touch-friendly control menu |

Press **H** to open the touch-friendly menu with large buttons for overlay visibility, timeline start/pause, and LRC reload. You can also use these in-game commands (including on touch-first setups with a command input):

- `/lyricsflow reload`
- `/lyricsflow toggle`
- `/lyricsflow play`
- `/lyricsflow pause`
- `/lyricsflow menu` — open the touch control screen

Example LRC:

    [00:00.00]First line
    [00:04.50]Second line
    [00:09.20]Third line

The HUD shows the active line and the next line. Keybinds can be changed in Minecraft Controls.

## Android companion setup

1. Install the **LyricsFlow Companion** debug APK on the same Android device as Zalith.
2. Open it and tap **Open notification access settings**.
3. Enable notification access for LyricsFlow Companion, then return to the app.
4. Start or resume music in a player that publishes an Android MediaSession.
5. Launch Minecraft with this Fabric mod. The mod polls the companion's loopback endpoint and uses the reported playback position to select the matching LRC line.

The companion binds only to `127.0.0.1:8765`; it is not intended to be reachable from other devices on the network. The mod polls off the Minecraft render thread. If no valid media session is available, it falls back to the manual in-game timeline. You still need a matching `lyricsflow/current.lrc` file.

Android permission is granted by the user in system settings. No root or Shizuku is required. This is an early prototype: Android background-process behavior, different music players, and the full Zalith runtime still require testing.

Sodium and Indium are not required by this mod and are not modified by it.

## License

MIT
