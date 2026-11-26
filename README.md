# LyricsFlow Android — Fabric 1.20.1

Lightweight client-side LRC lyric overlay for Minecraft Java 1.20.1, intended for Android Java launchers such as Zalith Launcher 2.

## Status

**Prototype: local LRC timeline only.** This version does not connect to YouTube Music, YouTube, Google OAuth, Android MediaSession, or another music player. The lyric clock is started and paused manually in Minecraft. Automatic music-player synchronization is not implemented.

## Build

- Minecraft 1.20.1
- Fabric Loader 0.15.11
- Fabric API 0.92.2+1.20.1
- Java 17

GitHub Actions builds the project on pushes and pull requests. Download the JAR from the successful workflow run's Artifacts section.

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

Example LRC:

    [00:00.00]First line
    [00:04.50]Second line
    [00:09.20]Third line

The HUD shows the active line and the next line. Keybinds can be changed in Minecraft Controls.

## Android notes

No root, Shizuku, or separate Android app is required. The current version uses Fabric client APIs only. Android MediaSession integration and device-specific Zalith runtime testing remain future work.

Sodium and Indium are not required by this mod and are not modified by it.

## License

MIT
