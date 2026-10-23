package com.phonefocus.guard;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.lang.ref.WeakReference;

/** Package names only, held in memory. No nodes, text, gestures, screenshots or network. */
public final class ScheduleAccessibilityService extends AccessibilityService {
    private static final String OVERLAY_CLASS = "com.phonefocus.guard.DailyBlockingOverlay";
    private static WeakReference<ScheduleAccessibilityService> instance = new WeakReference<>(null);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> allowed = new HashSet<>();
    private String foreground = "";
    private LinearLayout overlay;
    private TextView deadline;
    private boolean registered;
    static boolean connected() { return instance.get() != null; }
    static void refreshNow() { ScheduleAccessibilityService service = instance.get(); if (service != null) service.handler.post(service::evaluate); }
    private final Runnable tick = new Runnable() {
        @Override public void run() { evaluate(); handler.postDelayed(this, 1000); }
    };
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) { foreground = ""; hide(); }
            else evaluate();
        }
    };
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = new WeakReference<>(this);
        allowed.add(getPackageName()); allowed.add("android"); allowed.add("com.android.systemui");
        allowed.add("com.android.settings"); allowed.add("com.android.server.telecom");
        allowed.add("com.android.permissioncontroller"); allowed.add("com.google.android.permissioncontroller");
        addResolved(new Intent(Settings.ACTION_SETTINGS));
        addResolved(new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:")));
        TelecomManager telecom = getSystemService(TelecomManager.class);
        if (telecom != null) {
            String dialer = telecom.getDefaultDialerPackage(); if (dialer != null) allowed.add(dialer);
            if (Build.VERSION.SDK_INT >= 29) { String system = telecom.getSystemDialerPackage(); if (system != null) allowed.add(system); }
        }
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF); f.addAction(Intent.ACTION_SCREEN_ON); f.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(screen, f);
        registered = true;
        ScheduleManager.arm(this);
        handler.removeCallbacks(tick); handler.post(tick);
    }
    private void addResolved(Intent intent) {
        android.content.pm.ResolveInfo resolved = getPackageManager().resolveActivity(intent, 0);
        if (resolved != null && resolved.activityInfo != null) allowed.add(resolved.activityInfo.packageName);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        // Our overlay must not be mistaken for the app's settings activity and hide itself.
        if (OVERLAY_CLASS.contentEquals(event.getClassName() == null ? "" : event.getClassName())) return;
        foreground = event.getPackageName().toString();
        evaluate();
    }
    private void evaluate() {
        boolean visible = ScheduleManager.active(this) && !FocusManager.hasSession(this)
            && getSystemService(PowerManager.class).isInteractive()
            && !getSystemService(KeyguardManager.class).isKeyguardLocked()
            && !foreground.isEmpty() && !allowed.contains(foreground);
        if (!visible) { hide(); return; }
        if (overlay == null) show();
        if (deadline != null) {
            Instant next = ScheduleManager.schedule(this).nextChange(Instant.now(), ZoneId.systemDefault());
            deadline.setText(next == null ? "每日时段已连续覆盖全天\n请在时段设置中调整" : "恢复使用：" + DateTimeFormatter.ofPattern("MM月dd日 HH:mm").withZone(ZoneId.systemDefault()).format(next)
                + "\n剩余 " + FocusUi.duration(next.toEpochMilli() - System.currentTimeMillis()));
        }
    }
    private TextView label(String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(FocusUi.INK); t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0, 18, 0, 18); overlay.addView(t,p); return t;
    }
    private void button(String value, Runnable action) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false);
        overlay.addView(b, new LinearLayout.LayoutParams(-1, -2)); b.setOnClickListener(v -> action.run());
    }
    private void show() {
        overlay = new LinearLayout(this) {
            @Override public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
                super.onInitializeAccessibilityEvent(event); event.setClassName(OVERLAY_CLASS);
            }
            @Override public void sendAccessibilityEventUnchecked(AccessibilityEvent event) {
                event.setClassName(OVERLAY_CLASS); super.sendAccessibilityEventUnchecked(event);
            }
            @Override public boolean requestSendAccessibilityEvent(View child, AccessibilityEvent event) {
                event.setClassName(OVERLAY_CLASS); return super.requestSendAccessibilityEvent(child,event);
            }
        }; overlay.setOrientation(LinearLayout.VERTICAL); overlay.setGravity(Gravity.CENTER);
        int pad = (int)(28 * getResources().getDisplayMetrics().density); overlay.setPadding(pad,pad,pad,pad); overlay.setBackgroundColor(FocusUi.BG);
        label("去做正事", 32); label("现在是你设定的禁用时段", 19); deadline = label("", 18);
        label("放下手机，到点自动恢复。", 16);
        button("电话 / 紧急呼叫", () -> open(new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:"))));
        button("时段与授权设置", () -> open(new Intent(this, ScheduleSetupActivity.class)));
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(-1,-1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.OPAQUE);
        p.setTitle("PhoneFocusDailyOverlay"); p.gravity = Gravity.CENTER;
        try { getSystemService(WindowManager.class).addView(overlay,p); ScheduleManager.prefs(this).edit().remove("overlay_error").apply(); }
        catch (RuntimeException e) { overlay = null; deadline = null; ScheduleManager.prefs(this).edit().putString("overlay_error", "系统拒绝显示拦截页，请重新授权并检查后台设置。").apply(); }
    }
    private void open(Intent i) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (RuntimeException e) { android.widget.Toast.makeText(this, "请从系统入口打开电话或设置", android.widget.Toast.LENGTH_LONG).show(); }
    }
    private void hide() {
        if (overlay != null) {
            try { getSystemService(WindowManager.class).removeView(overlay); } catch (RuntimeException ignored) { }
            overlay = null; deadline = null;
        }
    }
    @Override public void onInterrupt() { evaluate(); }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null); hide();
        if (registered) { unregisterReceiver(screen); registered = false; }
        if (instance.get() == this) instance.clear();
        super.onDestroy();
    }
}
