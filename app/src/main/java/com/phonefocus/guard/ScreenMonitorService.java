package com.phonefocus.guard;

import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import java.util.Locale;

public final class ScreenMonitorService extends Service {
    public static final String ACTION_STOP = "com.phonefocus.guard.STOP";
    private static ScreenMonitorService instance;
    private AutoScreenOff session;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean registered;
    private long lastStatusAt;

    public static boolean isRunning() { return instance != null; }
    public static long elapsed() { return instance == null ? 0 : instance.session.elapsed(SystemClock.elapsedRealtime()); }
    public static long remaining() { return instance == null ? ScreenSession.INTERVAL_MS : instance.session.remaining(SystemClock.elapsedRealtime()); }
    public static boolean screenIsOn() { return instance != null && instance.session.isActive(); }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!getSystemService(PowerManager.class).isInteractive()) { screenOff(); return; }
            long now = SystemClock.elapsedRealtime();
            AutoScreenOff.Result result = session.poll(now, true);
            if (result == AutoScreenOff.Result.PERMISSION_REQUIRED || result == AutoScreenOff.Result.FAILED) {
                AppState.setEnabled(ScreenMonitorService.this, false);
                AppState.error(ScreenMonitorService.this, result == AutoScreenOff.Result.PERMISSION_REQUIRED
                    ? "锁屏授权未开启或已撤销，请重新授权后开启自动息屏。"
                    : "系统拒绝执行息屏，请用「立即息屏测试」检查权限后重新开启。");
                stopSelf();
                return;
            }
            if (result == AutoScreenOff.Result.LOCK_REQUESTED) { screenOff(); return; }
            if (now - lastStatusAt >= 15_000) updateStatus();
            handler.postDelayed(this, Math.max(1, Math.min(1000, session.remaining(now))));
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) screenOff();
            else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) screenOn();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        session = new AutoScreenOff(new DeviceLocker(this));
        Reminders.createChannels(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenReceiver, filter);
        registered = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if(FocusManager.hasSession(this)) { stopSelf(); return START_NOT_STICKY; }
        if ((intent != null && ACTION_STOP.equals(intent.getAction())) || !AppState.enabled(this)) {
            AppState.setEnabled(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!new DeviceLocker(this).isAuthorized()) {
            AppState.setEnabled(this, false);
            AppState.error(this, "请先在应用中授予锁屏权限，再开启自动息屏。");
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(Reminders.MONITOR_ID, Reminders.monitor(this, "连续亮屏 3 分钟自动息屏"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(Reminders.MONITOR_ID, Reminders.monitor(this, "连续亮屏 3 分钟自动息屏"));
            instance = this;
            AppState.error(this, "");
            if (getSystemService(PowerManager.class).isInteractive()) screenOn(); else screenOff();
            return START_STICKY;
        } catch (RuntimeException error) {
            AppState.error(this, "后台检测启动失败，请重新打开应用并点击开始检测。");
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    private void screenOn() {
        if (instance != this) return;
        session.screenOn(SystemClock.elapsedRealtime());
        handler.removeCallbacks(tick);
        updateStatus();
        handler.post(tick);
    }
    private void screenOff() {
        session.screenOff();
        handler.removeCallbacks(tick);
        getSystemService(NotificationManager.class).cancel(Reminders.REMINDER_ID);
        if (instance == this) updateStatus();
    }
    private void updateStatus() {
        lastStatusAt = SystemClock.elapsedRealtime();
        String text = session.isActive()
            ? String.format(Locale.CHINA, "本次亮屏 %d 分 %02d 秒 · 满 3 分钟自动息屏", elapsed() / 60_000, (elapsed() / 1000) % 60)
            : "屏幕已熄灭 · 下次亮屏重新计时";
        getSystemService(NotificationManager.class).notify(Reminders.MONITOR_ID, Reminders.monitor(this, text));
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (registered) unregisterReceiver(screenReceiver);
        session.screenOff();
        if (instance == this) instance = null;
        getSystemService(NotificationManager.class).cancel(Reminders.REMINDER_ID);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
