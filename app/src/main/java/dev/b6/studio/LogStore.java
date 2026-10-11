package dev.b6.studio;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Bounded in-memory diagnostics. No automatic disk writes. Callers use the main thread. */
public final class LogStore {
  public static final int MAX_ENTRIES = 500, MAX_CHARS = 65536, MAX_LINE = 512;

  public static final class Entry {
    public final long id;
    public final String text;

    Entry(long id, String text) {
      this.id = id;
      this.text = text;
    }
  }

  private final ArrayDeque<Entry> entries = new ArrayDeque<>();
  private final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);
  private final Date date = new Date();
  private int chars;
  private long nextId, revision;

  public void add(String message, long now) {
    date.setTime(now);
    String prefix = time.format(date) + "  ";
    int limit = MAX_LINE - prefix.length();
    String line =
        prefix + (message.length() > limit ? message.substring(0, limit - 1) + "…" : message);
    entries.addLast(new Entry(++nextId, line));
    chars += line.length();
    revision++;
    while (entries.size() > MAX_ENTRIES || chars > MAX_CHARS)
      chars -= entries.removeFirst().text.length();
  }

  public void clear() {
    entries.clear();
    chars = 0;
    revision++;
  }

  public long revision() {
    return revision;
  }

  public int size() {
    return entries.size();
  }

  public int chars() {
    return chars;
  }

  public List<Entry> snapshot() {
    return new ArrayList<>(entries);
  }

  public String text() {
    StringBuilder s = new StringBuilder(chars + entries.size());
    for (Entry e : entries) {
      if (s.length() > 0) s.append('\n');
      s.append(e.text);
    }
    return s.toString();
  }
}
