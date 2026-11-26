package dev.lyricsflow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dependency-free LRC parser. */
public final class LrcTrack {
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private final List<Entry> entries;

    private LrcTrack(List<Entry> entries) { this.entries = entries; }

    public static LrcTrack parse(String text) {
        List<Entry> result = new ArrayList<>();
        for (String line : text.split("\\R")) {
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
        return new LrcTrack(List.copyOf(result));
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
    public record Entry(long timeMs, String text) {}
}
