package com.phonefocus.guard;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioManager;
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

/** Package events drive overlays; separately opted-in screenshots use Agnes cloud vision. */
public final class ScheduleAccessibilityService extends AccessibilityService {
    private static final String OVERLAY_CLASS = "com.phonefocus.guard.DailyBlockingOverlay";
    private static WeakReference<ScheduleAccessibilityService> instance = new WeakReference<>(null);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> allowed = new HashSet<>();
    private String foreground = "";
    private String beforeScreenOff = "";
    private LinearLayout overlay;
    private TextView deadline;
    private TextView reason;
    private boolean registered;
    private long foregroundRevision;
    private EntertainmentMonitor entertainment;
    static boolean connected() { return instance.get() != null; }
    static void refreshNow() {
        ScheduleAccessibilityService service=instance.get();
        if (service!=null) service.handler.post(() -> {
            service.foregroundRevision++;
            if (service.entertainment!=null) service.entertainment.invalidate();
            service.evaluate();
            if (service.entertainment!=null) service.entertainment.poll();
        });
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() { evaluate(); if (entertainment!=null) entertainment.poll(); handler.postDelayed(this, 1000); }
    };
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            foregroundRevision++;
            if (entertainment!=null) entertainment.invalidate();
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                try { EntertainmentBlock.checkpoint(ScheduleAccessibilityService.this); } catch (RuntimeException ignored) { }
                beforeScreenOff=foreground; foreground = ""; hide();
            }
            else {
                if (Intent.ACTION_USER_PRESENT.equals(i.getAction()) && (foreground.isEmpty() || foreground.equals("com.android.systemui"))) foreground=beforeScreenOff;
                evaluate();
            }
        }
    };
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = new WeakReference<>(this);
        allowed.add(getPackageName()); allowed.add("android"); allowed.add("com.android.systemui");
        allowed.add("com.android.settings"); allowed.add("com.android.server.telecom");
        allowed.add("com.android.phone"); allowed.add("com.android.emergency");
        allowed.add("com.android.permissioncontroller"); allowed.add("com.google.android.permissioncontroller");
        addResolved(new Intent(Settings.ACTION_SETTINGS));
        addResolved(new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:")));
        addResolved(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
        TelecomManager telecom = getSystemService(TelecomManager.class);
        if (telecom != null) {
            String dialer = telecom.getDefaultDialerPackage(); if (dialer != null) allowed.add(dialer);
            if (Build.VERSION.SDK_INT >= 29) { String system = telecom.getSystemDialerPackage(); if (system != null) allowed.add(system); }
        }
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF); f.addAction(Intent.ACTION_SCREEN_ON); f.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(screen, f);
        registered = true;
        entertainment = new EntertainmentMonitor(this);
        try { EntertainmentBlock.checkpoint(this); } catch (RuntimeException ignored) { }
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
        foregroundRevision++;
        if (entertainment!=null) entertainment.invalidate();
        evaluate();
    }
    String foregroundPackage() { return foreground; }
    long foregroundRevision() { return foregroundRevision; }
    boolean entertainmentEligible() {
        if (!EntertainmentSettings.supported() || overlay!=null || EntertainmentBlock.active(this) || FocusManager.hasSession(this)
                || foreground.isEmpty() || allowed.contains(foreground)) return false;
        PowerManager power=getSystemService(PowerManager.class);
        KeyguardManager keyguard=getSystemService(KeyguardManager.class);
        AudioManager audio=getSystemService(AudioManager.class);
        if (power==null || !power.isInteractive() || keyguard==null || keyguard.isKeyguardLocked() || audio==null) return false;
        int mode=audio.getMode();
        return mode==AudioManager.MODE_NORMAL; // Skip calls, ringing and VoIP, including floating call UI.
    }
    private void evaluate() {
        long blockRemaining=EntertainmentBlock.remaining(this);
        boolean daily=ScheduleManager.active(this);
        AudioManager audio=getSystemService(AudioManager.class);
        boolean visible = (daily || blockRemaining>0) && !FocusManager.hasSession(this)
            && getSystemService(PowerManager.class).isInteractive()
            && !getSystemService(KeyguardManager.class).isKeyguardLocked()
            && audio!=null && audio.getMode()==AudioManager.MODE_NORMAL
            && !foreground.isEmpty() && !allowed.contains(foreground);
        if (!visible) { hide(); return; }
        if (overlay == null) show();
        if (reason!=null) reason.setText(blockRemaining>0?"识别到娱乐，当前禁止使用手机":"现在是你设定的禁用时段");
        if (deadline != null) {
            String text="";
            if (blockRemaining>0) text="娱乐禁用剩余 "+FocusUi.duration(blockRemaining)+"\n本次结束："
                +DateTimeFormatter.ofPattern("MM月dd日 HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(System.currentTimeMillis()+blockRemaining));
            if (daily) {
                Instant next=ScheduleManager.schedule(this).nextChange(Instant.now(),ZoneId.systemDefault());
                if (!text.isEmpty()) text+="\n\n每日计划也在生效\n";
                text+=next==null?"每日时段已连续覆盖全天": "每日恢复："+DateTimeFormatter.ofPattern("MM月dd日 HH:mm").withZone(ZoneId.systemDefault()).format(next)
                    +"\n剩余 "+FocusUi.duration(next.toEpochMilli()-System.currentTimeMillis());
            }
            deadline.setText(text);
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
        label("去做正事", 32); reason=label("",19); deadline = label("", 18);
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
            overlay = null; deadline = null; reason=null;
        }
    }
    @Override public void onInterrupt() { foregroundRevision++; if (entertainment!=null) entertainment.invalidate(); evaluate(); }
    @Override public void onDestroy() {
        if (entertainment!=null) entertainment.close();
        handler.removeCallbacksAndMessages(null); hide();
        if (registered) { unregisterReceiver(screen); registered = false; }
        if (instance.get() == this) instance.clear();
        super.onDestroy();
    }
}
