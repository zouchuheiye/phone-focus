package com.phonefocus.guard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class FocusAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        FocusManager.reconcile(c);
        if (FocusManager.hasSession(c)) {
            try { FocusManager.ensureService(c); }
            catch (RuntimeException failure) { FocusManager.prefs(c).edit().putString("error","守护恢复失败，请打开应用使用恢复入口。").commit(); }
        }
    }
}
