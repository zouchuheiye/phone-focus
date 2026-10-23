package com.phonefocus.guard;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.UserManager;
import android.net.Uri;
import android.provider.Settings;
import android.telecom.TelecomManager;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashSet;

final class FocusManager {
    static final String ACTIVE = "active", PREPARING = "preparing", RESTORING = "restoring";
    static final String[] RESTRICTIONS = {UserManager.DISALLOW_APPS_CONTROL, UserManager.DISALLOW_CONFIG_DATE_TIME, UserManager.DISALLOW_SAFE_BOOT};
    private FocusManager() {}
    static SharedPreferences prefs(Context c) {
        return c.createDeviceProtectedStorageContext().getSharedPreferences("focus_owner_v1", Context.MODE_PRIVATE);
    }
    static String phase(Context c) { return prefs(c).getString("phase", ""); }
    static boolean hasSession(Context c) { return !phase(c).isEmpty(); }
    static boolean isOwner(Context c) {
        DevicePolicyManager dpm = c.getSystemService(DevicePolicyManager.class);
        return dpm != null && dpm.isDeviceOwnerApp(c.getPackageName());
    }
    static boolean exactAlarmAllowed(Context c) {
        return Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }
    static int boot(Context c) { return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1); }
    static Intent dialer(Context c) {
        String packageName=c.getSystemService(TelecomManager.class).getDefaultDialerPackage();
        if(packageName==null || packageName.isEmpty())throw new IllegalStateException("未找到默认电话应用。");
        return new Intent(Intent.ACTION_DIAL,Uri.parse("tel:")).setPackage(packageName);
    }
    private static String[] focusPackages(Context c,ComponentName defaultDialer){
        LinkedHashSet<String> packages=new LinkedHashSet<>();packages.add(c.getPackageName());packages.add(defaultDialer.getPackageName());
        // Android routes emergency calls via the preinstalled phone app even when another dialer is default.
        if(Build.VERSION.SDK_INT>=29){
            String systemDialer=c.getSystemService(TelecomManager.class).getSystemDialerPackage();
            if(systemDialer!=null&&!systemDialer.isEmpty())packages.add(systemDialer);
        }
        return packages.toArray(new String[0]);
    }
    static String availability(Context c) {
        if (Build.VERSION.SDK_INT < 28) return "强制专注需要 Android 9 或以上版本。";
        if (!isOwner(c)) return "尚未配置设备所有者。安装 APK 或激活普通设备管理员不会自动取得此权限。";
        if (boot(c) < 0) return "无法确认重启状态，不能可靠恢复定时专注。";
        if (!exactAlarmAllowed(c)) return "请先允许「闹钟和提醒」权限，以便准时结束。";
        try {
            Intent emergency = dialer(c);
            if (emergency.resolveActivity(c.getPackageManager()) == null) return "未找到系统紧急拨号界面，无法开启强制专注。";
        } catch (RuntimeException unsupported) { return "系统未提供可用的紧急拨号入口。"; }
        return "";
    }
    static FocusWindow window(Context c) {
        SharedPreferences p = prefs(c);
        return new FocusWindow(p.getLong("wall_end", -1), p.getLong("elapsed_end", -1),
            p.getLong("checkpoint_elapsed", -1), p.getLong("saved_remaining", -1), p.getInt("boot", -1));
    }
    static long remaining(Context c) {
        if (!ACTIVE.equals(phase(c))) return 0;
        try { return window(c).remaining(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot(c)); }
        catch (RuntimeException corrupt) { return 0; }
    }
    private static SharedPreferences.Editor putWindow(SharedPreferences.Editor editor, FocusWindow w) {
        return editor.putLong("wall_end", w.wallEnd).putLong("elapsed_end", w.elapsedEnd)
            .putLong("checkpoint_elapsed", w.checkpointElapsed).putLong("saved_remaining", w.savedRemaining).putInt("boot", w.bootCount);
    }
    static void checkpoint(Context c) {
        FocusWindow next = window(c).checkpoint(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot(c));
        if (!putWindow(prefs(c).edit(), next).commit()) throw new IllegalStateException("Cannot persist deadline");
    }
    static synchronized void start(Activity c, long duration, String recoveryCode) {
        if(Build.VERSION.SDK_INT<28)throw new IllegalStateException("需要Android 9或以上版本。");
        if (hasSession(c)) throw new IllegalStateException("已有专注任务或策略待恢复。");
        String unavailable = availability(c);
        if (!unavailable.isEmpty()) throw new IllegalStateException(unavailable);
        if (c.getSystemService(KeyguardManager.class).isKeyguardLocked()) throw new IllegalStateException("请先解锁手机再开始专注。");
        DevicePolicyManager dpm = c.getSystemService(DevicePolicyManager.class);
        ComponentName admin = DeviceLocker.admin(c);
        FocusWindow w = FocusWindow.start(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot(c), duration);
        SharedPreferences.Editor edit = prefs(c).edit().clear().putString("phase", PREPARING)
            .putString("old_packages", new JSONArray(Arrays.asList(dpm.getLockTaskPackages(admin))).toString())
            .putInt("old_features", dpm.getLockTaskFeatures(admin))
            .putInt("old_home_component",c.getPackageManager().getComponentEnabledSetting(new ComponentName(c.getPackageName(),c.getPackageName()+".FocusHome")))
            .putBoolean("old_uninstall", dpm.isUninstallBlocked(admin, c.getPackageName()))
            .putBoolean("resume_monitor", AppState.enabled(c)).putString("recovery_hash", RecoveryCode.hash(recoveryCode));
        Bundle old = dpm.getUserRestrictions(admin);
        for (String key : RESTRICTIONS) edit.putBoolean("old_"+key, old.getBoolean(key));
        if (!putWindow(edit, w).commit()) throw new IllegalStateException("无法保存恢复信息，未启动专注。");
        try {
            // Reserve the expiry callback before changing any device policy.
            schedule(c, duration);
            Intent emergency = dialer(c);
            ComponentName emergencyComponent = emergency.resolveActivity(c.getPackageManager());
            dpm.setLockTaskPackages(admin, focusPackages(c,emergencyComponent));
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS | DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD);
            dpm.setUninstallBlocked(admin, c.getPackageName(), true);
            for (String key : RESTRICTIONS) dpm.addUserRestriction(admin, key);
            IntentFilter home = new IntentFilter(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME); home.addCategory(Intent.CATEGORY_DEFAULT);
            if (!prefs(c).edit().putBoolean("home_touched", true).commit()) throw new IllegalStateException("无法保存主屏幕恢复标记。");
            c.getPackageManager().setComponentEnabledSetting(new ComponentName(c.getPackageName(),c.getPackageName()+".FocusHome"),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
            dpm.addPersistentPreferredActivity(admin, home, new ComponentName(c.getPackageName(), c.getPackageName()+".FocusHome"));
            if (!dpm.isLockTaskPermitted(c.getPackageName())) throw new IllegalStateException("系统未允许锁定任务模式。");
            if (!prefs(c).edit().putString("phase", ACTIVE).commit()) throw new IllegalStateException("无法保存专注状态。");
            AppState.setEnabled(c, false);
            c.stopService(new Intent(c, ScreenMonitorService.class));
            ensureService(c);
            open(c);
        } catch (RuntimeException failure) {
            release(c, "启动未完成，已尝试恢复策略。");
            throw failure;
        }
    }
    static void open(Context c) {
        c.startActivity(new Intent(c, FocusActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }
    static void ensureService(Context c) { c.startForegroundService(new Intent(c, FocusService.class)); }
    static synchronized boolean release(Context c, String reason) {
        SharedPreferences p = prefs(c);
        if (!hasSession(c)) return true;
        // Write phase before removing allowlisting: Android can finish the locked Activity immediately.
        if (!p.edit().putString("phase", RESTORING).commit()) return false;
        DevicePolicyManager dpm = c.getSystemService(DevicePolicyManager.class);
        ComponentName admin = DeviceLocker.admin(c);
        List<PolicyRollback.Step> steps = new ArrayList<>();
        if (isOwner(c)) {
            steps.add(new PolicyRollback.Step("锁定任务允许列表", () -> {
                try {
                    JSONArray stored = new JSONArray(p.getString("old_packages", "[]"));
                    String[] packages = new String[stored.length()];
                    for (int i=0;i<packages.length;i++) packages[i]=stored.getString(i);
                    dpm.setLockTaskPackages(admin, packages);
                } catch (org.json.JSONException corrupt) { throw new IllegalStateException(corrupt); }
            }));
            if(Build.VERSION.SDK_INT>=28)steps.add(new PolicyRollback.Step("系统界面策略", () -> dpm.setLockTaskFeatures(admin, p.getInt("old_features", DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS))));
            steps.add(new PolicyRollback.Step("卸载限制", () -> dpm.setUninstallBlocked(admin, c.getPackageName(), p.getBoolean("old_uninstall", false))));
            for (String key : RESTRICTIONS) steps.add(new PolicyRollback.Step(key, () -> {
                if (p.getBoolean("old_"+key, false)) dpm.addUserRestriction(admin,key); else dpm.clearUserRestriction(admin,key);
            }));
            if (p.getBoolean("home_touched", false)) steps.add(new PolicyRollback.Step("主屏幕", () -> dpm.clearPackagePersistentPreferredActivities(admin,c.getPackageName())));
        }
        if(p.getBoolean("home_touched",false))steps.add(new PolicyRollback.Step("临时主屏幕入口",()->c.getPackageManager().setComponentEnabledSetting(
            new ComponentName(c.getPackageName(),c.getPackageName()+".FocusHome"),p.getInt("old_home_component",PackageManager.COMPONENT_ENABLED_STATE_DEFAULT),PackageManager.DONT_KILL_APP)));
        List<String> errors = PolicyRollback.run(steps);
        if (!errors.isEmpty()) {
            p.edit().putString("error", "策略尚未全部恢复，请重试或使用紧急恢复："+String.join("；",errors)).commit();
            return false;
        }
        boolean resume = p.getBoolean("resume_monitor", false);
        c.getSystemService(AlarmManager.class).cancel(alarm(c));
        boolean unlocked=c.getSystemService(UserManager.class).isUserUnlocked();
        if (!p.edit().clear().putString("last_result",reason).putBoolean("resume_monitor_after_unlock",resume&&!unlocked).commit()) return false;
        if (unlocked) {
            AppState.setEnabled(c, resume);
            if (resume && new DeviceLocker(c).isAuthorized()) {
                try { c.startForegroundService(new Intent(c,ScreenMonitorService.class)); }
                catch (RuntimeException denied) { AppState.error(c,"专注已结束；请打开应用恢复普通息屏检测。"); }
            }
        }
        c.stopService(new Intent(c,FocusService.class));
        return true;
    }
    static boolean recover(Context c, String code) {
        if (!RecoveryCode.matches(code, prefs(c).getString("recovery_hash", ""))) return false;
        return release(c,"已通过手机外保存的恢复码提前结束。");
    }
    @SuppressWarnings("deprecation")
    static boolean emergencyRemoveOwner(Context c,String code){
        // Last resort is only reachable after the external secret has been verified again.
        if(!RecoveryCode.matches(code,prefs(c).getString("recovery_hash","")))return false;
        try{
            if(isOwner(c))c.getSystemService(DevicePolicyManager.class).clearDeviceOwnerApp(c.getPackageName());
            if(isOwner(c))return false;
            c.getPackageManager().setComponentEnabledSetting(new ComponentName(c.getPackageName(),c.getPackageName()+".FocusHome"),
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,PackageManager.DONT_KILL_APP);
            c.getSystemService(AlarmManager.class).cancel(alarm(c));
            if(!prefs(c).edit().clear().putString("last_result","紧急解除设备管理，已停止强制专注。").commit())return false;
            if(c.getSystemService(UserManager.class).isUserUnlocked())AppState.setEnabled(c,false);
            c.stopService(new Intent(c,FocusService.class));
            return true;
        }catch(RuntimeException failed){return false;}
    }
    static synchronized void reconcile(Context c) {
        String phase = phase(c);
        if (phase.isEmpty()) return;
        if (!ACTIVE.equals(phase) || !isOwner(c) || remaining(c) <= 0) {
            release(c,ACTIVE.equals(phase) ? "专注时间已到，使用限制已解除。" : "未完成的策略变更已恢复。");
            return;
        }
        try { checkpoint(c); schedule(c,remaining(c)); }
        catch (RuntimeException failure) {
            // If the required scheduling capability disappears, release rather than extend an unbounded lock.
            release(c,"准时结束能力不可用，已结束本次专注。");
        }
    }
    private static PendingIntent alarm(Context c) {
        return PendingIntent.getBroadcast(c,42,new Intent(c,FocusAlarmReceiver.class),PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private static void schedule(Context c, long duration) {
        AlarmManager alarms = c.getSystemService(AlarmManager.class);
        if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) throw new SecurityException("Exact alarm not allowed");
        alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime()+Math.max(1,duration),alarm(c));
    }
    static void restoreOnBoot(Context c) {
        reconcile(c);
        if (hasSession(c)) {
            try { ensureService(c); }
            catch (RuntimeException denied) { prefs(c).edit().putString("error","系统未允许启动守护服务，请解锁并打开应用检查。").commit(); }
        }
    }
}
