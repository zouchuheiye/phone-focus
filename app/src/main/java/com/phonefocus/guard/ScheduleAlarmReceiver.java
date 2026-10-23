package com.phonefocus.guard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ScheduleAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) { ScheduleManager.changed(context); }
}
