package com.phonefocus.guard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

/** Persistent temporary app restriction. Screen off, detector pause and duration edits do not clear it. */
final class EntertainmentBlock {
    private EntertainmentBlock() { }
    static SharedPreferences prefs(Context c) { return c.createDeviceProtectedStorageContext().getSharedPreferences("entertainment_block_v1",Context.MODE_PRIVATE); }
    private static int boot(Context c) { return Math.max(0,Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,0)); }
    private static FocusWindow load(Context c) {
        SharedPreferences p=prefs(c);
        if (!p.contains("wall_end")) return null;
        try { return new FocusWindow(p.getLong("wall_end",0),p.getLong("elapsed_end",0),p.getLong("checkpoint",0),p.getLong("remaining",0),p.getInt("boot",0)); }
        catch (IllegalArgumentException e) { return null; }
    }
    private static void save(Context c,FocusWindow w) {
        if (!prefs(c).edit().putLong("wall_end",w.wallEnd).putLong("elapsed_end",w.elapsedEnd)
            .putLong("checkpoint",w.checkpointElapsed).putLong("remaining",w.savedRemaining).putInt("boot",w.bootCount).commit())
            throw new IllegalStateException("Cannot persist entertainment block");
    }
    static long remaining(Context c) {
        FocusWindow w=load(c);
        return w==null?0:w.remaining(System.currentTimeMillis(),SystemClock.elapsedRealtime(),boot(c));
    }
    static boolean active(Context c) { return remaining(c)>0; }
    static void start(Context c) {
        if (active(c)) return;
        save(c,FocusWindow.start(System.currentTimeMillis(),SystemClock.elapsedRealtime(),boot(c),EntertainmentSettings.blockMinutes(c)*60_000L));
        ScheduleAccessibilityService.refreshNow();
    }
    static void checkpoint(Context c) {
        FocusWindow w=load(c);if (w==null || remaining(c)<=0) return;
        save(c,w.checkpoint(System.currentTimeMillis(),SystemClock.elapsedRealtime(),boot(c)));
    }
}
