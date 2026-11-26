package dev.lyricsflow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dependency-free LRC parser, including common ID tags. */
public final class LrcTrack {
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern META = Pattern.compile("^\\[(ti|ar|al):([^]]*)]$", Pattern.CASE_INSENSITIVE);
    private final List<Entry> entries;
    private final String title;
    private final String artist;
    private final String album;

    private LrcTrack(List<Entry> entries, String title, String artist, String album) {
        this.entries = entries;
        this.title = title;
        this.artist = artist;
        this.album = album;
    }

    public static LrcTrack parse(String text) {
        List<Entry> result = new ArrayList<>();
        String title = "";
        String artist = "";
        String album = "";
        for (String line : text.split("\\R")) {
            Matcher metadata = META.matcher(line.trim());
            if (metadata.matches()) {
                String key = metadata.group(1).toLowerCase(java.util.Locale.ROOT);
                String value = metadata.group(2).trim();
                if (key.equals("ti")) title = value;
                else if (key.equals("ar")) artist = value;
                else if (key.equals("al")) album = value;
                continue;
            }
            Matcher matcher = TIME.matcher(line);
            List<Long> timestamps = new ArrayList<>();
            while (matcher.find()) {
                long minutes = Long.parseLong(matcher.group(1));
                long seconds = Long.parseLong(matcher.group(2));
                String fraction = matcher.group(3);
                long millis = fraction == null ? 0 : Long.parseLong(fraction);
                if (fraction != null && fraction.length() == 1) millis *= 100;
                else if (fraction != null && fraction.length() == 2) millis *= 10;
                timestamps.add((minutes * 60 + seconds) * 1000 + millis);
            }
            String lyric = TIME.matcher(line).replaceAll("").trim();
            for (long timestamp : timestamps) if (!lyric.isEmpty()) result.add(new Entry(timestamp, lyric));
        }
        result.sort(Comparator.comparingLong(Entry::timeMs));
        return new LrcTrack(List.copyOf(result), title, artist, album);
    }

    public Entry current(long positionMs) {
        Entry current = null;
        for (Entry entry : entries) {
            if (entry.timeMs() > positionMs) break;
            current = entry;
        }
        return current;
    }
    public Entry next(long positionMs) {
        for (Entry entry : entries) if (entry.timeMs() > positionMs) return entry;
        return null;
    }
    public boolean isEmpty() { return entries.isEmpty(); }
    public String title() { return title; }
    public String artist() { return artist; }
    public String album() { return album; }
    public long durationMs() { return entries.isEmpty() ? 0L : entries.get(entries.size() - 1).timeMs(); }
    public record Entry(long timeMs, String text) {}
}
