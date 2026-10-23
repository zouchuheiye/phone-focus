package com.phonefocus.guard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(245,244,239);
    private static final int INK = Color.rgb(35,51,42);
    private static final int GREEN = Color.rgb(66,102,83);
    private static final int MUTED = Color.rgb(106,119,110);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status, detail, permission, lastReminder;
    private Button toggle;
    private TimerView timer;
    private boolean pendingStart;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { render(); handler.postDelayed(this, 1000); }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pendingStart = savedInstanceState != null && savedInstanceState.getBoolean("pending_start", false);
        Reminders.createChannels(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setBackgroundColor(BG);
        frame.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(frame);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        frame.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout page = column();
        page.setPadding(dp(24), dp(24), dp(24), dp(28));
        scroll.addView(page);

        TextView eyebrow = text("给注意力，留一点空白", 12, MUTED);
        page.addView(eyebrow);
        TextView title = text("去做正事", 32, INK);
        title.setTypeface(null, Typeface.BOLD);
        page.addView(title, margin(-1, -2, 8, 0));
        page.addView(text("手机放下，生活继续。", 15, MUTED), margin(-1, -2, 8, 24));
        Button owner=button("每日禁用时段 · 只用手机设置",false);
        owner.setOnClickListener(v->startActivity(new Intent(this,ScheduleSetupActivity.class)));
        page.addView(owner,margin(-1,dp(54),0,18));

        LinearLayout card = column();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(20), dp(22), dp(20), dp(22));
        card.setBackground(shape(Color.WHITE, 26));
        page.addView(card, margin(-1, -2, 0, 18));
        status = text("尚未开始检测", 14, GREEN);
        card.addView(status);
        timer = new TimerView();
        card.addView(timer, new LinearLayout.LayoutParams(dp(240), dp(240)));
        detail = text("连续亮屏满 3 分钟，自动息屏并锁屏。", 14, MUTED);
        detail.setGravity(Gravity.CENTER);
        card.addView(detail, margin(-1, -2, 0, 8));
        toggle = button("开启自动息屏", true);
        toggle.setOnClickListener(v -> {
            if (ScreenMonitorService.isRunning()) stopMonitor(); else beginSetup();
        });
        card.addView(toggle, margin(-1, dp(54), 16, 0));

        LinearLayout rule = column();
        rule.setPadding(dp(20), dp(18), dp(20), dp(18));
        rule.setBackground(shape(Color.rgb(232,237,229), 20));
        rule.addView(text("亮屏 3 分钟，自动放下手机", 17, INK));
        rule.addView(text("后台检测连续亮屏，满 3 分钟自动锁屏。\n熄屏立即归零，下次亮屏重新计时。\n锁屏界面亮着也计时，息屏显示不计时。", 14, MUTED), margin(-1, -2, 10, 0));
        page.addView(rule, margin(-1, -2, 0, 18));

        page.addView(text("授权一次，后台自动执行", 17, INK));
        permission = text("", 13, MUTED);
        page.addView(permission, margin(-1, -2, 8, 10));
        Button test = button("立即息屏测试", false);
        test.setOnClickListener(v -> testScreenOff());
        page.addView(test, margin(-1, dp(50), 0, 8));
        Button notifications = button("后台状态通知设置", false);
        notifications.setOnClickListener(v -> openNotifications());
        page.addView(notifications, margin(-1, dp(50), 0, 8));
        Button battery = button("后台运行设置", false);
        battery.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("允许后台持续检测")
            .setMessage("请在系统设置中允许本应用后台运行，将电池策略设为「不受限制」；部分手机还需开启「自启动」。\n\n开启后可返回桌面，应用在后台连续检测。开机和应用更新后会尝试自动恢复。若手动强行停止应用，需重新打开应用。")
            .setPositiveButton("打开系统设置", (d, w) -> safeOpen(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)))
            .setNegativeButton("知道了", null).show());
        page.addView(battery, margin(-1, dp(50), 0, 12));
        Button revoke = button("关闭并解除锁屏授权", false);
        revoke.setOnClickListener(v -> revokeAdmin());
        page.addView(revoke, margin(-1, dp(50), 0, 12));
        lastReminder = text("", 12, MUTED);
        page.addView(lastReminder, margin(-1, -2, 6, 8));
        page.addView(text("锁屏后可能需要输入 PIN、图案或密码解锁。\n卸载前请先解除锁屏授权。仅本机检测，无需联网。", 12, MUTED), margin(-1, -2, 0, 0));
        render();
    }

    private void beginSetup() {
        if (!DeviceLocker.supported(this)) {
            Toast.makeText(this, "这台设备不支持系统锁屏授权，无法自动息屏。", Toast.LENGTH_LONG).show();
            return;
        }
        if (new DeviceLocker(this).isAuthorized()) { startMonitor(true); return; }
        pendingStart = true;
        Intent activate = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, DeviceLocker.admin(this))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.lock_admin_description));
        try { startActivityForResult(activate, 201); }
        catch (ActivityNotFoundException failure) {
            pendingStart = false;
            Toast.makeText(this, "系统不支持锁屏授权页面，无法开启自动息屏。", Toast.LENGTH_LONG).show();
        }
    }
    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != 201) return;
        pendingStart = false;
        if (new DeviceLocker(this).isAuthorized()) startMonitor(true);
        else {
            AppState.setEnabled(this, false);
            Toast.makeText(this, "未授予锁屏权限，自动息屏没有开启。", Toast.LENGTH_LONG).show();
            render();
        }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 101) { pendingStart = false; startMonitor(false); }
    }
    private void startMonitor(boolean askNotification) {
        if (!new DeviceLocker(this).isAuthorized()) {
            AppState.setEnabled(this, false);
            render();
            return;
        }
        if (askNotification && !Reminders.permissionGranted(this)) {
            pendingStart = true;
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 101);
            return;
        }
        // A denied notification permission must not disable the independent device-admin action.
        AppState.setEnabled(this, true);
        try {
            startForegroundService(new Intent(this, ScreenMonitorService.class));
            if (askNotification) Toast.makeText(this, "已开启；返回桌面后，连续亮屏满 3 分钟自动息屏。", Toast.LENGTH_LONG).show();
        } catch (RuntimeException failure) {
            AppState.setEnabled(this, false);
            AppState.error(this, "后台检测启动失败，请允许后台运行后重试。");
        }
        render();
    }
    private void testScreenOff() {
        DeviceLocker locker = new DeviceLocker(this);
        if (!locker.isAuthorized()) {
            Toast.makeText(this, "请先点击「授权并开启自动息屏」。", Toast.LENGTH_LONG).show();
            return;
        }
        try { locker.lockNow(); }
        catch (RuntimeException failure) {
            AppState.error(this, "息屏指令失败，请检查系统锁屏授权。");
            Toast.makeText(this, "系统未允许息屏，请检查锁屏授权。", Toast.LENGTH_LONG).show();
        }
    }
    private void revokeAdmin() {
        if(FocusManager.hasSession(this)){FocusManager.open(this);return;}
        if(FocusManager.isOwner(this)){startActivity(new Intent(this,OwnerSetupActivity.class));return;}
        stopMonitor();
        try {
            DevicePolicyManager policy = getSystemService(DevicePolicyManager.class);
            if (policy != null && policy.isAdminActive(DeviceLocker.admin(this))) policy.removeActiveAdmin(DeviceLocker.admin(this));
            Toast.makeText(this, "已停止，并请求解除锁屏授权。", Toast.LENGTH_LONG).show();
        } catch (RuntimeException failure) {
            Toast.makeText(this, "请在系统设置的「设备管理应用」中关闭「去做正事」。", Toast.LENGTH_LONG).show();
        }
        render();
    }
    private void stopMonitor() {
        AppState.setEnabled(this, false);
        AppState.error(this, "");
        stopService(new Intent(this, ScreenMonitorService.class));
        render();
    }
    private void openNotifications() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        safeOpen(intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }
    private void safeOpen(Intent intent) {
        try { startActivity(intent); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "请到系统设置中找到「去做正事」。", Toast.LENGTH_LONG).show(); }
    }
    private void render() {
        boolean running = ScreenMonitorService.isRunning();
        boolean on = ScreenMonitorService.screenIsOn();
        boolean authorized = new DeviceLocker(this).isAuthorized();
        status.setText(running ? (on ? "● 自动息屏已开启 · 后台检测中" : "● 已开启 · 等待亮屏")
            : AppState.enabled(this) ? "后台检测待恢复，请重新开启" : "自动息屏已关闭");
        detail.setText(running ? (on ? "距离自动息屏 " + duration(ScreenMonitorService.remaining()) : "下一次亮屏，将重新开始计时")
            : "连续亮屏满 3 分钟，自动息屏并锁屏。");
        toggle.setText(running ? "停止自动息屏" : authorized ? "开启自动息屏" : "授权并开启自动息屏");
        toggle.setBackground(shape(running ? Color.rgb(86,105,92) : GREEN, 16));
        timer.elapsed = ScreenMonitorService.elapsed();
        timer.running = running;
        timer.invalidate();
        timer.setContentDescription("本次连续亮屏 " + duration(timer.elapsed));
        String notice = authorized ? "锁屏权限已开启。" : "请先授权锁屏，才能自动息屏。";
        notice += Reminders.canNotify(this) ? "状态通知已开启。" : "状态通知未开启，不影响已授权的锁屏动作。";
        boolean unrestricted = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        String batteryStatus = unrestricted ? "电池优化已放行，请按手机品牌允许自启动。" : "请允许后台运行和自启动，避免检测被系统暂停。";
        String error = AppState.prefs(this).getString("error", "");
        permission.setText(getString(R.string.permission_status, notice, batteryStatus, error));
        String last = AppState.prefs(this).getString("last_lock_request", "");
        lastReminder.setText(last.isEmpty() ? "还没有发出过息屏指令。" : "上次息屏指令：" + last);
    }

    @Override protected void onResume() {
        super.onResume();
        FocusManager.reconcile(this);
        if(FocusManager.hasSession(this)){FocusManager.open(this);return;}
        if (!pendingStart && AppState.enabled(this) && !ScreenMonitorService.isRunning() && new DeviceLocker(this).isAuthorized()) startMonitor(false);
        handler.removeCallbacks(refresh); handler.post(refresh);
    }
    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("pending_start", pendingStart);
        super.onSaveInstanceState(outState);
    }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    private static String duration(long milliseconds) {
        long seconds = (milliseconds + 999) / 1000;
        return String.format(Locale.CHINA, "%02d:%02d", seconds / 60, seconds % 60);
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setLineSpacing(dp(3), 1);
        return view;
    }
    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label); button.setTextSize(15); button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : GREEN);
        button.setBackground(shape(primary ? GREEN : Color.WHITE, 16));
        button.setStateListAnimator(null);
        return button;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    private LinearLayout.LayoutParams margin(int width, int height, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height); p.setMargins(0, dp(top), 0, dp(bottom)); return p;
    }

    private final class TimerView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        long elapsed;
        boolean running;
        TimerView() { super(MainActivity.this); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float radius = Math.min(cx, cy) - dp(22);
            bounds.set(cx-radius, cy-radius, cx+radius, cy+radius);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(8)); paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.rgb(235,239,232)); canvas.drawOval(bounds, paint);
            paint.setColor(running ? GREEN : Color.rgb(189,200,185));
            float fraction = Math.min(1f, elapsed / (float) ScreenSession.INTERVAL_MS);
            canvas.drawArc(bounds, -90, running ? Math.max(2, 360 * fraction) : 0, false, paint);
            paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setColor(INK); paint.setTextSize(dp(42));
            canvas.drawText(duration(elapsed), cx, cy + dp(8), paint);
            paint.setTypeface(Typeface.DEFAULT); paint.setColor(MUTED); paint.setTextSize(dp(12));
            canvas.drawText("本次连续亮屏", cx, cy + dp(34), paint);
        }
    }
}
