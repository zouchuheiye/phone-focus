package com.phonefocus.guard;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public final class LockAdminReceiver extends DeviceAdminReceiver {
    @Override public void onDisabled(Context context, Intent intent) {
        FocusManager.release(context,"设备管理授权已关闭。");
        AppState.setEnabled(context, false);
        AppState.error(context, "锁屏授权已关闭，自动息屏已停止。");
        context.stopService(new Intent(context, ScreenMonitorService.class));
    }
    @Override public void onLockTaskModeEntering(Context context,Intent intent,String packageName){
        if(context.getPackageName().equals(packageName)&&FocusManager.hasSession(context))
            FocusManager.prefs(context).edit().putLong("system_confirmed_at",System.currentTimeMillis()).commit();
    }
    @Override public void onLockTaskModeExiting(Context context,Intent intent){
        if(FocusManager.ACTIVE.equals(FocusManager.phase(context))&&FocusManager.remaining(context)>0){
            try{FocusManager.ensureService(context);}catch(RuntimeException ignored){}
        }
    }
}
