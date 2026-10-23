package com.phonefocus.guard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.AlarmManager;
import android.os.UserManager;

public final class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
            && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) && !Intent.ACTION_USER_UNLOCKED.equals(action)
            && !Intent.ACTION_TIME_CHANGED.equals(action) && !Intent.ACTION_TIMEZONE_CHANGED.equals(action) && !AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) return;
        ScheduleManager.changed(context);
        FocusManager.restoreOnBoot(context);
        if(FocusManager.hasSession(context) || !context.getSystemService(UserManager.class).isUserUnlocked())return;
        if(FocusManager.prefs(context).getBoolean("resume_monitor_after_unlock",false)){
            AppState.setEnabled(context,true);
            FocusManager.prefs(context).edit().remove("resume_monitor_after_unlock").commit();
        }
        if(Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action) || AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action))return;
        if (!AppState.enabled(context)) return;
        if (!new DeviceLocker(context).isAuthorized()) {
            AppState.setEnabled(context, false);
            AppState.error(context, "锁屏授权未开启，自动息屏未恢复。请打开应用授权。");
            return;
        }
        try { context.startForegroundService(new Intent(context, ScreenMonitorService.class)); }
        catch (RuntimeException error) {
            AppState.error(context, "系统未允许自动恢复，请打开应用点击开始检测。");
        }
    }
}
