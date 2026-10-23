package com.phonefocus.guard;

/** A monotonic, continuous interactive-screen session. No wall clock or Android dependencies. */
public final class ScreenSession {
    public static final long INTERVAL_MS = 180_000L;
    private long startedAt = -1;
    private long nextReminderAt = -1;

    public void screenOn(long now) {
        if (startedAt >= 0) return; // A duplicate broadcast must never postpone a reminder.
        startedAt = now;
        nextReminderAt = now + INTERVAL_MS;
    }

    public void screenOff() { startedAt = -1; nextReminderAt = -1; }
    public boolean isActive() { return startedAt >= 0; }
    public long elapsed(long now) { return isActive() ? Math.max(0, now - startedAt) : 0; }
    public long remaining(long now) { return isActive() ? Math.max(0, nextReminderAt - now) : INTERVAL_MS; }

    public boolean poll(long now) {
        if (!isActive() || now < nextReminderAt) return false;
        // If the main thread was delayed, deliver one reminder; do not replay a burst.
        nextReminderAt = now + INTERVAL_MS;
        return true;
    }
}
