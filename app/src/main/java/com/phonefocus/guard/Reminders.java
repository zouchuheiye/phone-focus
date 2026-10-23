package com.phonefocus.guard;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

final class Reminders {
    static final String MONITOR_CHANNEL = "monitor";
    static final int MONITOR_ID = 1;
    static final int REMINDER_ID = 2; // Cancel an old 1.0.0 reminder on upgrade.

    private Reminders() {}
    static void createChannels(Context c) {
        NotificationChannel monitor = new NotificationChannel(MONITOR_CHANNEL, "自动息屏检测状态", NotificationManager.IMPORTANCE_LOW);
        monitor.setDescription("连续亮屏满3分钟自动息屏；可在通知中停止");
        monitor.setShowBadge(false);
        c.getSystemService(NotificationManager.class).createNotificationChannel(monitor);
    }
    static boolean permissionGranted(Context c) {
        return Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
    static boolean canNotify(Context c) {
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel(MONITOR_CHANNEL);
        return permissionGranted(c) && manager.areNotificationsEnabled()
            && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }
    static Notification monitor(Context c, String text) {
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(c, 1,
            new Intent(c, ScreenMonitorService.class).setAction(ScreenMonitorService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(c, MONITOR_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("去做正事 · 自动息屏已开启")
            .setContentText(text).setContentIntent(open).setOngoing(true)
            .setOnlyAlertOnce(true).setShowWhen(false).setCategory(Notification.CATEGORY_SERVICE)
            .addAction(new Notification.Action.Builder(null, "停止自动息屏", stop).build()).build();
    }
}
