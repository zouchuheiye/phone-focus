package com.phonefocus.guard;

/** Monotonic ten-minute cadence. No wakeups, catch-up bursts or wall-clock dependence. */
public final class EntertainmentCadence {
    public static final long INTERVAL_MS=10*60*1000L;
    public static final long RESULT_MAX_AGE_MS=45_000;
    private long next=-1;
    public boolean poll(long elapsed, boolean enabled) {
        if (!enabled) { next=-1; return false; }
        if (next<0) { next=elapsed+INTERVAL_MS; return false; }
        if (elapsed<next) return false;
        next=elapsed+INTERVAL_MS; return true;
    }
    public static boolean current(boolean enabled, boolean eligible, long capturedRevision, long currentRevision,
                                  String capturedPackage, String currentPackage, long ageMs) {
        return enabled && eligible && capturedRevision==currentRevision && capturedPackage!=null
            && !capturedPackage.isEmpty() && capturedPackage.equals(currentPackage) && ageMs>=0 && ageMs<RESULT_MAX_AGE_MS;
    }
}
