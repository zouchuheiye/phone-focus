package com.phonefocus.guard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** Local wall-clock recurrence, start inclusive, end exclusive. Never stores a password. */
final class DailySchedule {
    static final int MAX_WINDOWS = 8;
    static final class Window {
        final int start, end;
        final boolean enabled;
        Window(int start, int end) { this(start, end, true); }
        Window(int start, int end, boolean enabled) {
            if (start < 0 || start >= 1440 || end < 0 || end >= 1440 || start == end)
                throw new IllegalArgumentException("请选择不同的开始和结束时间");
            this.start = start; this.end = end; this.enabled = enabled;
        }
        boolean contains(int minute) { return enabled && (start < end ? minute >= start && minute < end : minute >= start || minute < end); }
        String label() { return clock(start) + " — " + (end < start ? "次日 " : "") + clock(end); }
    }
    final List<Window> windows;
    DailySchedule(List<Window> windows) {
        if (windows.size() > MAX_WINDOWS) throw new IllegalArgumentException("最多设置 8 个时段");
        this.windows = Collections.unmodifiableList(new ArrayList<>(windows));
    }
    boolean hasEnabledWindows() {
        for (Window window : windows) if (window.enabled) return true;
        return false;
    }
    boolean active(Instant now, ZoneId zone) {
        java.time.LocalTime t = now.atZone(zone).toLocalTime();
        int minute = t.getHour() * 60 + t.getMinute();
        for (Window w : windows) if (w.contains(minute)) return true;
        return false;
    }
    /** Returns only real changes of the union, including DST jumps and repeated hours. */
    Instant nextChange(Instant now, ZoneId zone) {
        TreeSet<Instant> candidates = new TreeSet<>();
        LocalDate today = now.atZone(zone).toLocalDate();
        for (int d = -1; d <= 3; d++) {
            for (Window w : windows) {
                if (!w.enabled) continue;
                for (int minute : new int[]{w.start, w.end}) {
                    LocalDateTime local = today.plusDays(d).atTime(minute / 60, minute % 60);
                    List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
                    for (ZoneOffset offset : offsets) candidates.add(local.toInstant(offset));
                    if (offsets.isEmpty()) candidates.add(zone.getRules().getTransition(local).getInstant());
                }
            }
        }
        ZoneOffsetTransition transition = zone.getRules().nextTransition(now);
        if (transition != null && transition.getInstant().isBefore(now.plusSeconds(4 * 86400L)))
            candidates.add(transition.getInstant());
        for (Instant candidate : candidates)
            if (candidate.isAfter(now) && active(candidate.minusNanos(1), zone) != active(candidate, zone)) return candidate;
        return null;
    }
    String encode() {
        StringBuilder value = new StringBuilder();
        for (Window w : windows) {
            if (value.length() > 0) value.append(';');
            value.append(w.start).append(',').append(w.end).append(',').append(w.enabled ? '1' : '0');
        }
        return value.toString();
    }
    static DailySchedule decode(String value) {
        ArrayList<Window> windows = new ArrayList<>();
        if (!value.isEmpty()) for (String item : value.split(";", -1)) {
            String[] pair = item.split(",", -1);
            if (pair.length != 2 && pair.length != 3) throw new IllegalArgumentException("时段数据损坏，请重新设置");
            if (pair.length == 3 && !pair[2].equals("0") && !pair[2].equals("1"))
                throw new IllegalArgumentException("时段开关数据损坏，请重新设置");
            // 1.3.0 stored two columns: preserve every old window as enabled on upgrade.
            windows.add(new Window(Integer.parseInt(pair[0]), Integer.parseInt(pair[1]), pair.length == 2 || pair[2].equals("1")));
        }
        return new DailySchedule(windows);
    }
    static String clock(int minute) { return String.format(Locale.CHINA, "%02d:%02d", minute / 60, minute % 60); }
}
