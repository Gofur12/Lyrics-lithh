# LyricsFlow — Fabric 1.20.1 (Android / Zalith)

Client-side LRC lyric overlay and touch-friendly playlist interface for Minecraft Java 1.20.1. Designed to run as **one Fabric JAR** in Android Java launchers such as Zalith Launcher 2.

## Current status

This is an early prototype. Working features include:

- LRC parsing and an in-game lyric HUD.
- Touch-friendly menu and lyric timing offset controls.
- Local playlist metadata (playlist names and track-title entries).
- Manual internal lyric timeline.
- Optional playback metadata import from a properties file.

**Not implemented:** actual audio playback, YouTube playback/login, Spotify playback/login, Android music-library browsing, or automatic Android media-session access. The YouTube, Spotify, Musik HP, and App tabs are interface placeholders; they do not currently connect to those services. A Fabric JAR alone cannot provide a native Android WebView or Android notification-listener service.

## Build

- Minecraft 1.20.1
- Fabric Loader 0.15.11
- Fabric API 0.92.2+1.20.1
- Java 17
- Gradle 8.7

GitHub Actions builds the Fabric JAR. Download the `lyricsflow-fabric-1.20.1` artifact from the workflow run's **Artifacts** section.

## Install and use

1. Download the Fabric JAR artifact.
2. Put the JAR in the Minecraft instance's `mods` folder.
3. Launch Minecraft 1.20.1 with Fabric.
4. On first launch, the mod creates `lyricsflow/current.lrc` in the game directory.
5. Replace it with an LRC file. For a song-specific file, put `<Song Title>.lrc` in the same `lyricsflow` folder.

| Key | Action |
| --- | --- |
| O | Show or hide lyrics |
| P | Start or pause the internal lyric timeline |
| L | Reload lyrics |
| H | Open the touch-friendly menu |

The menu includes overlay, timeline, reload, and timing-offset controls. Positive offsets delay lyrics; negative offsets advance them. The offset is saved in `lyricsflow/settings.properties` and limited to ±60,000 ms.

Commands:

- `/lyricsflow reload`
- `/lyricsflow toggle`
- `/lyricsflow play`
- `/lyricsflow pause`
- `/lyricsflow menu`
- `/lyricsflow offset 500`
- `/lyricsflow offset -500`
- `/lyricsflow offset reset`

Example LRC:

    [00:00.00]First line
    [00:04.50]Second line
    [00:09.20]Third line

## Optional playback metadata file

The mod can read `lyricsflow/nowplaying.properties`. This is only a metadata input; it does not play audio or control another app. A separate tool would need to keep this file updated.

Example contents:

    title=Song Title
    artist=Artist Name
    position_ms=12500
    playing=true
    updated_at_ms=1795698000000

`updated_at_ms` must be the current Unix time in milliseconds and refreshed at least every 15 seconds. The mod checks the file twice per second. When a fresh title is detected, it tries to load `lyricsflow/Song Title.lrc`; if that file is missing, it uses `lyricsflow/current.lrc`. Invalid or stale metadata is ignored.

## Playlist notes

Playlists currently store track-title entries only. Selecting a track loads its matching LRC file (or the fallback `current.lrc`). Playlist controls do not start audio playback.

Sodium and Indium are not required and are not modified by this mod.

## License

MIT
