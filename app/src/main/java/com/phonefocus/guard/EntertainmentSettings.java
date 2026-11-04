package com.phonefocus.guard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

final class EntertainmentSettings {
    private EntertainmentSettings() { }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("entertainment_guard",Context.MODE_PRIVATE); }
    static boolean supported() { return Build.VERSION.SDK_INT>=30; }
    static int blockMinutes(Context c) {
        int value=prefs(c).getInt("block_minutes",30);
        return value>=1 && value<=1440?value:30;
    }
    static void setBlockMinutes(Context c,int minutes) {
        if (minutes<1 || minutes>1440) throw new IllegalArgumentException("请输入1—1440分钟");
        if (!prefs(c).edit().putInt("block_minutes",minutes).commit()) throw new IllegalStateException("保存失败，请重试");
        ScheduleAccessibilityService.refreshNow();
    }
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
