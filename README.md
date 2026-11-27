# LyricsFlow — Fabric 1.20.1 (Android / Zalith)

Client-side LRC lyric overlay and touch-friendly music/playlist interface for Minecraft Java 1.20.1. Built as one Fabric JAR for Android Java launchers such as Zalith Launcher 2.

## Implemented

- LRC parsing, lyric HUD, current/next lyric preview, and reload.
- Touch-friendly in-game menu with Player, Lirik, Spotify, YouTube, Musik HP, Playlist, and App tabs.
- Internal lyric timeline controls: play, pause, stop, and ±5-second seek. This timeline does not control audio playback.
- Lyric timing offset controls (±100 ms) and persistent settings.
- Playlist create, rename, delete, track add/remove, and selection. Playlist data is saved to `lyricsflow/playlists.json`.
- Optional external now-playing metadata import from `lyricsflow/nowplaying.properties`.
- YouTube Data API v3 song/video search displayed inside the YouTube tab, including result titles, channel names, thumbnails, and paginated results.
- Spotify account connection using Authorization Code with PKCE, plus in-game track metadata search. The Spotify Client ID is a public app identifier; no Client Secret is embedded.

## Not implemented / limitations

- **No audio playback** from YouTube, Spotify, or local Android music.
- Selecting a YouTube result only records its video ID/title in the UI; it does not open or play the video.
- Spotify login/search requires an internet connection and a Spotify account. The login callback uses the local loopback address `127.0.0.1:8888`; Android/Zalith browser handoff still needs device testing.
- Spotify search results are metadata only; selecting a result does not play it.
- Android media-session access and Android notification-listener integration are not provided by this Fabric JAR.
- The Musik HP tab is currently a placeholder.
- The now-playing properties file is only a metadata bridge. A separate program would need to update it; this mod does not create that bridge app.

A Fabric client mod runs inside Minecraft and does not automatically gain Android app permissions or access to Android's media library.

## Build

- Minecraft 1.20.1
- Fabric Loader 0.15.11
- Fabric API 0.92.2+1.20.1
- Java 17
- Gradle 8.7

GitHub Actions builds the JAR. Download the `lyricsflow-fabric-1.20.1` artifact from the workflow run's **Artifacts** section.

## Install

1. Download the `lyricsflow-fabric-1.20.1` artifact ZIP from GitHub Actions and extract the JAR.
2. Put the JAR in the Minecraft 1.20.1 Fabric instance's `mods` folder.
3. Launch the instance. The mod creates its `lyricsflow` data folder in the game directory.

## LRC files

Place a default lyric file at `lyricsflow/current.lrc`. For a song-specific file, put `<Song Title>.lrc` in the same folder. Example:

```lrc
[ti:Song Title]
[ar:Artist Name]
[00:00.00]First line
[00:04.50]Second line
[00:09.20]Third line
```

Positive offsets delay lyrics; negative offsets advance them. Offset settings are saved in `lyricsflow/settings.properties`.

## Spotify setup

1. The Spotify Client ID is preconfigured in `lyricsflow/services.properties` when the mod first launches.
2. In the Spotify tab, tap **Hubungkan Spotify**. The mod opens the browser for Spotify authorization.
3. Approve access and wait for the local callback page, then return to Minecraft and search for a track.
4. The refresh token is stored locally in `lyricsflow/spotify-token.properties`. Keep this file private and do not upload or share it.
5. The Spotify Developer Dashboard redirect URI must exactly match `http://127.0.0.1:8888/callback`.

The integration uses Authorization Code with PKCE and does not require a Client Secret in the JAR. It only searches track metadata; it does not provide audio playback.

## YouTube search setup

1. Enable **YouTube Data API v3** in a Google Cloud project.
2. Create an API key and keep it private.
3. Launch the game once so the mod creates `lyricsflow/services.properties`.
4. Add your key to that local file:

```properties
youtube_api_key=YOUR_PRIVATE_API_KEY
```

Do not commit or share `services.properties`. The key is used for YouTube search metadata only. YouTube Data API search does not provide an audio stream for playback.

## Optional now-playing metadata

The mod can read `lyricsflow/nowplaying.properties`. This is only a metadata input; it does not play audio or control another app.

```properties
title=Song Title
artist=Artist Name
position_ms=12500
playing=true
updated_at_ms=1795698000000
```

Update `updated_at_ms` to the current Unix time in milliseconds and refresh it at least every 15 seconds. A separate tool must maintain this file. When fresh metadata is detected, the mod tries to load `lyricsflow/Song Title.lrc`; if missing, it falls back to `lyricsflow/current.lrc`.

## Controls

| Key | Action |
| --- | --- |
| O | Show or hide lyrics |
| P | Start or pause the internal lyric timeline |
| L | Reload lyrics |
| H | Open the touch-friendly menu |

Commands:

- `/lyricsflow reload`
- `/lyricsflow toggle`
- `/lyricsflow play`
- `/lyricsflow pause`
- `/lyricsflow menu`
- `/lyricsflow offset 500`
- `/lyricsflow offset -500`
- `/lyricsflow offset reset`

## Playlist format

Playlist tracks can be entered as `Title | filename.lrc`. Entering only a title lets the mod try to find a matching LRC file automatically. Playlist selection loads lyrics; it does not start music playback.

Sodium and Indium are not required and are not modified by this mod.

## License

MIT
