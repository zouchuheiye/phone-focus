package com.phonefocus.guard;

import android.content.Context;
import android.content.SharedPreferences;

final class AppState {
    private AppState() {}
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("monitor", Context.MODE_PRIVATE); }
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", false); }
    static void setEnabled(Context c, boolean enabled) { prefs(c).edit().putBoolean("enabled", enabled).apply(); }
    static void error(Context c, String message) { prefs(c).edit().putString("error", message).apply(); }
}
