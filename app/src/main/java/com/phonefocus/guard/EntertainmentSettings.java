package com.phonefocus.guard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

final class EntertainmentSettings {
    private EntertainmentSettings() { }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("entertainment_guard",Context.MODE_PRIVATE); }
    static boolean supported() { return Build.VERSION.SDK_INT>=30; }
    static boolean enabled(Context c) { return supported() && prefs(c).getBoolean("enabled",false); }
    static void setEnabled(Context c,boolean on) {
        prefs(c).edit().putBoolean("enabled",on && supported()).putString("last_result",on?"等待首次10分钟检查":"已暂停娱乐检查").apply();
        ScheduleAccessibilityService.refreshNow();
    }
    static void result(Context c,String message) {
        prefs(c).edit().putLong("last_check",System.currentTimeMillis()).putString("last_result",message).apply();
    }
}
