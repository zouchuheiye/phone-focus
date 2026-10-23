package com.phonefocus.guard;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import java.time.Instant;
import java.time.ZoneId;

final class ScheduleManager {
    static SharedPreferences prefs(Context c) { return c.createDeviceProtectedStorageContext().getSharedPreferences("daily_schedule_v1", Context.MODE_PRIVATE); }
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", false); }
    static DailySchedule schedule(Context c) {
        try { return DailySchedule.decode(prefs(c).getString("windows", "")); }
        catch (IllegalArgumentException e) { return DailySchedule.decode(""); }
    }
    static void save(Context c, DailySchedule schedule, boolean enabled) {
        if (!prefs(c).edit().putString("windows", schedule.encode()).putBoolean("enabled", enabled && !schedule.windows.isEmpty()).commit())
            throw new IllegalStateException("保存失败，请重试");
        changed(c);
    }
    static boolean active(Context c) { return enabled(c) && schedule(c).active(Instant.now(), ZoneId.systemDefault()); }
    static boolean permissionEnabled(Context c) {
        String list = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName wanted = new ComponentName(c, ScheduleAccessibilityService.class);
        if (list != null) for (String item : list.split(":")) if (wanted.equals(ComponentName.unflattenFromString(item))) return true;
        return false;
    }
    static boolean exactAllowed(Context c) { return Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms(); }
    static void changed(Context c) { arm(c); ScheduleAccessibilityService.refreshNow(); }
    static void arm(Context c) {
        AlarmManager alarms = c.getSystemService(AlarmManager.class);
        PendingIntent pending = PendingIntent.getBroadcast(c, 1300, new Intent(c, ScheduleAlarmReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.cancel(pending);
        if (!enabled(c)) return;
        Instant next = schedule(c).nextChange(Instant.now(), ZoneId.systemDefault());
        if (next == null) return;
        try {
            if (exactAllowed(c)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), pending);
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), pending);
            prefs(c).edit().remove("alarm_error").apply();
        } catch (RuntimeException e) {
            prefs(c).edit().putString("alarm_error", "系统未允许时段闹钟；请检查后台及闹钟权限。").apply();
        }
    }
}
