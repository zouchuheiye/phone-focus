package com.phonefocus.guard;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.os.UserManager;

public final class FocusService extends Service {
    private static final int ID = 21;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastCheckpoint;
    private long lastOpen;
    private final BroadcastReceiver userPresent = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { showFocusIfReady(); }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!FocusManager.hasSession(FocusService.this)) { stopSelf(); return; }
            if (!FocusManager.ACTIVE.equals(FocusManager.phase(FocusService.this)) || FocusManager.remaining(FocusService.this) <= 0) {
                FocusManager.release(FocusService.this,"专注时间已到，使用限制已解除。");
                if (!FocusManager.hasSession(FocusService.this)) { stopSelf(); return; }
            }
            long now = SystemClock.elapsedRealtime();
            if (now-lastCheckpoint >= 15_000) {
                lastCheckpoint=now;
                try {
                    if (FocusManager.ACTIVE.equals(FocusManager.phase(FocusService.this))) FocusManager.checkpoint(FocusService.this);
                    getSystemService(NotificationManager.class).notify(ID,notification());
                } catch (RuntimeException failure) { FocusManager.release(FocusService.this,"无法保存专注状态，已尝试解除限制。"); }
            }
            // The HOME policy restores the focus activity on navigation; no polling launch over emergency calls.
            handler.postDelayed(this,1000);
        }
    };
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this,21,new Intent(this,FocusActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"focus_owner").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("去做正事 · 定时专注进行中")
            .setContentText(FocusManager.ACTIVE.equals(FocusManager.phase(this)) ? "剩余 "+FocusUi.duration(FocusManager.remaining(this))+" · 到时解除限制" : "策略恢复中，请打开应用检查")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setCategory(Notification.CATEGORY_SERVICE).build();
    }
    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("focus_owner","定时专注状态",NotificationManager.IMPORTANCE_LOW));
        if (Build.VERSION.SDK_INT>=33) registerReceiver(userPresent,new IntentFilter(Intent.ACTION_USER_PRESENT),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(userPresent,new IntentFilter(Intent.ACTION_USER_PRESENT));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        try {
            if (Build.VERSION.SDK_INT>=34) startForeground(ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(ID,notification());
            FocusManager.reconcile(this);
            if (!FocusManager.hasSession(this)) { stopSelf(); return START_NOT_STICKY; }
            handler.removeCallbacks(tick); handler.post(tick);
            showFocusIfReady();
            return START_STICKY;
        } catch (RuntimeException failure) {
            FocusManager.prefs(this).edit().putString("error","专注守护启动失败，已尝试恢复策略。").commit();
            FocusManager.release(this,"守护启动失败，专注已结束。");
            stopSelf(); return START_NOT_STICKY;
        }
    }
    private void showFocusIfReady() {
        if (!FocusManager.ACTIVE.equals(FocusManager.phase(this))) return;
        if (!getSystemService(UserManager.class).isUserUnlocked() || getSystemService(KeyguardManager.class).isKeyguardLocked()
            || !getSystemService(PowerManager.class).isInteractive()) return;
        long now=SystemClock.elapsedRealtime();
        if (now-lastOpen<1500) return;
        lastOpen=now;
        try { FocusManager.open(this); }
        catch (RuntimeException denied) { FocusManager.prefs(this).edit().putString("error","无法自动显示专注页；请按主屏幕键或打开应用。").commit(); }
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null); unregisterReceiver(userPresent);
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
