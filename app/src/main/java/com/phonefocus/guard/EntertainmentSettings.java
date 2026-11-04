package com.phonefocus.guard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

final class EntertainmentSettings {
    private EntertainmentSettings() { }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("entertainment_guard",Context.MODE_PRIVATE); }
    static boolean supported() { return Build.VERSION.SDK_INT>=30; }
    // New key intentionally requires fresh consent after upgrading from the offline OCR version.
    static boolean enabled(Context c) { return supported() && prefs(c).getBoolean("agnes_enabled_v1",false) && AgnesConfig.present(c); }
    static void setEnabled(Context c,boolean on) {
        prefs(c).edit().putBoolean("agnes_enabled_v1",on && supported() && AgnesConfig.present(c)).putString("last_result",on?"等待首次10分钟Agnes检查":"已暂停Agnes娱乐检查").apply();
        ScheduleAccessibilityService.refreshNow();
    }
    static void result(Context c,String message) {
        prefs(c).edit().putLong("last_check",System.currentTimeMillis()).putString("last_result",message).apply();
    }
}
