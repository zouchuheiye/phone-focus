package com.phonefocus.guard;

/** Persistable deadline: monotonic within a boot, wall deadline bounded by saved remaining after reboot. */
public final class FocusWindow {
    public static final long MAX_DURATION_MS = 24 * 60 * 60_000L;
    public final long wallEnd, elapsedEnd, checkpointElapsed, savedRemaining;
    public final int bootCount;

    public FocusWindow(long wallEnd, long elapsedEnd, long checkpointElapsed, long savedRemaining, int bootCount) {
        if (wallEnd < 0 || elapsedEnd < 0 || checkpointElapsed < 0 || savedRemaining < 0
                || savedRemaining > MAX_DURATION_MS || bootCount < 0) throw new IllegalArgumentException("Invalid focus deadline");
        this.wallEnd = wallEnd; this.elapsedEnd = elapsedEnd; this.checkpointElapsed = checkpointElapsed;
        this.savedRemaining = savedRemaining; this.bootCount = bootCount;
    }
    public static FocusWindow start(long wall, long elapsed, int boot, long duration) {
        if (duration < 1000 || duration > MAX_DURATION_MS) throw new IllegalArgumentException("Duration must be 1 second to 24 hours");
        return new FocusWindow(Math.addExact(wall, duration), Math.addExact(elapsed, duration), elapsed, duration, boot);
    }
    public long remaining(long wall, long elapsed, int boot) {
        if (boot == bootCount && elapsed >= checkpointElapsed) return Math.max(0, elapsedEnd - elapsed);
        // Clock rollback on a reboot can never extend beyond the last saved remaining duration.
        return Math.max(0, Math.min(savedRemaining, wallEnd - wall));
    }
    public FocusWindow checkpoint(long wall, long elapsed, int boot) {
        long left = remaining(wall, elapsed, boot);
        return new FocusWindow(wallEnd, Math.addExact(elapsed, left), elapsed, left, boot);
    }
}
