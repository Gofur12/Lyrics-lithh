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

## External player bridge (companion protocol)

An optional companion process can update `lyricsflow/nowplaying.properties`. The mod checks this file every 10 game ticks (about twice per second). When the file is fresh (updated within 15 seconds), its playback position drives the LRC timeline and its title/artist appear in the HUD. If the file is missing or stale, the mod falls back to its manual in-game clock.

Example file (UTF-8 Java Properties format):

    title=Song title
    artist=Artist name
    position_ms=12500
    playing=true
    updated_at_ms=1795651200000

The companion must refresh `updated_at_ms` regularly and write the current playback position in milliseconds. For reliable updates, write a temporary file and atomically rename it to `nowplaying.properties`. The timestamp is Unix epoch milliseconds. This is a file-based handoff, not direct MediaSession integration; no Android permissions or media-player access are provided by the Minecraft mod.

## Android notes

No root, Shizuku, or separate Android app is required. The current version uses Fabric client APIs only. The Android companion that reads MediaSession and writes this bridge file, plus device-specific Zalith runtime testing, remain future work.

Sodium and Indium are not required by this mod and are not modified by it.

## License

MIT
