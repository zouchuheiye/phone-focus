package com.phonefocus.guard;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import java.text.DateFormat;
import java.util.Date;

final class DeviceLocker implements AutoScreenOff.Locker {
    private final Context context;
    DeviceLocker(Context context) { this.context = context; }
    static ComponentName admin(Context context) { return new ComponentName(context, LockAdminReceiver.class); }
    static boolean supported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN);
    }
    @Override public boolean isAuthorized() {
        DevicePolicyManager policy = context.getSystemService(DevicePolicyManager.class);
        return supported(context) && policy != null && policy.isAdminActive(admin(context));
    }
    @Override public void lockNow() {
        if (!isAuthorized()) throw new SecurityException("Device administrator not active");
        context.getSystemService(DevicePolicyManager.class).lockNow();
        // lockNow is void; this records the request, not a claim of physical-screen verification.
        AppState.prefs(context).edit().putString("last_lock_request",
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date())).apply();
        AppState.error(context, "");
    }
}
