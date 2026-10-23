package com.phonefocus.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public final class ScheduleSetupActivity extends Activity {
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { @Override public void run() { renderStatus(); handler.postDelayed(this, 1000); } };
    @Override public void onCreate(Bundle state) { super.onCreate(state); build(); }
    private void build() {
        LinearLayout page = FocusUi.page(this);
        FocusUi.text(this,page,"去做正事",32,FocusUi.INK);
        FocusUi.text(this,page,"每天到点，自动放下手机",20,FocusUi.GREEN);
        FocusUi.text(this,page,"只需在手机设置一次。每日重复执行，无需每天点击开始；支持跨午夜和多个时段。",15,FocusUi.MUTED);
        status = FocusUi.text(this,page,"",16,FocusUi.GREEN);
        DailySchedule s = ScheduleManager.schedule(this);
        FocusUi.text(this,page,"每日时段  " + s.windows.size() + " / " + DailySchedule.MAX_WINDOWS,18,FocusUi.INK);
        FocusUi.text(this,page,"每段单独开关。点时间框修改，时段之间恢复使用。",14,FocusUi.MUTED);
        for (int n = 0; n < s.windows.size(); n++) {
            addTimeRangeCard(page,s,n);
        }
        if (s.windows.isEmpty()) FocusUi.text(this,page,"还没有时段。例：09:00—12:00，或22:00—次日07:00。",15,FocusUi.MUTED);
        FocusUi.button(this,page,"＋ 添加每日禁用时段",false,() -> edit(-1));
        FocusUi.button(this,page,ScheduleManager.enabled(this)?"暂停全部时段":"开启每日计划",true,() -> {
            if (ScheduleManager.enabled(this)) { save(s,false); return; }
            if (s.windows.isEmpty()) { toast("请先添加至少一个时间段"); return; }
            if (!s.hasEnabledWindows()) { toast("请先打开至少一个时段的开关"); return; }
            save(s,true);
            if (!ScheduleManager.permissionEnabled(this)) disclosure();
        });
        FocusUi.button(this,page,"手机授权 · 无障碍服务",false,this::disclosure);
        FocusUi.button(this,page,"准时执行 · 闹钟权限",false,() -> {
            if (android.os.Build.VERSION.SDK_INT >= 31) safeOpen(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+getPackageName())));
            else toast("此系统无需单独开启闹钟权限");
        });
        FocusUi.button(this,page,"后台与自启动设置",false,() -> new AlertDialog.Builder(this).setTitle("保持每日自动执行")
            .setMessage("请将本应用电池策略设为「不受限制」，并按手机品牌允许「自启动」。\n\n应用被强行停止、无障碍被关闭或系统清理时，拦截可能暂停。重开应用并检查授权后恢复。")
            .setPositiveButton("打开应用设置",(d,w)->safeOpen(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())))).setNegativeButton("返回",null).show());
        FocusUi.text(this,page,"时段内用专注页面拦截普通应用，到结束时间自动撤下。电话、紧急呼叫、系统设置及本应用的配置入口保留。",14,FocusUi.MUTED);
        FocusUi.text(this,page,"手机授权模式无法阻止本人关闭权限、修改时段或卸载；不修改系统密码，也不能保证所有安卓型号持续运行。",13,FocusUi.MUTED);
        FocusUi.button(this,page,"可选功能 · 连续亮屏3分钟息屏",false,()->startActivity(new Intent(this,MainActivity.class)));
        FocusUi.button(this,page,"高级 · 受管设备模式",false,()->startActivity(new Intent(this,OwnerSetupActivity.class)));
        renderStatus();
    }
    private void addTimeRangeCard(LinearLayout page,DailySchedule s,int index) {
        DailySchedule.Window window = s.windows.get(index);
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        int padding = FocusUi.dp(this,14); card.setPadding(padding,padding,padding,padding);
        card.setBackground(rectangle(Color.WHITE,Color.rgb(221,229,220)));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1,-2); layout.setMargins(0,FocusUi.dp(this,6),0,FocusUi.dp(this,10));
        page.addView(card,layout);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); card.addView(header);
        TextView name = new TextView(this); name.setText("时段 " + (index+1)); name.setTextSize(14); name.setTextColor(FocusUi.MUTED);
        header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle = new Switch(this); toggle.setText(window.enabled?"开启":"关闭"); toggle.setTextColor(FocusUi.GREEN); toggle.setTextSize(14);
        toggle.setChecked(window.enabled); toggle.setMinimumHeight(FocusUi.dp(this,48));
        toggle.setContentDescription("时段"+(index+1)+"，"+window.label()); header.addView(toggle);
        toggle.setOnCheckedChangeListener((button,on) -> {
            ArrayList<DailySchedule.Window> list = new ArrayList<>(ScheduleManager.schedule(this).windows);
            if (index >= list.size()) { build(); return; }
            DailySchedule.Window old = list.get(index); list.set(index,new DailySchedule.Window(old.start,old.end,on));
            save(new DailySchedule(list),ScheduleManager.enabled(this));
        });
        LinearLayout times = new LinearLayout(this); times.setGravity(Gravity.CENTER_VERTICAL); card.addView(times,new LinearLayout.LayoutParams(-1,-2));
        timeField(times,"开始时间",DailySchedule.clock(window.start),index,window.enabled);
        TextView dash = new TextView(this); dash.setText("—"); dash.setGravity(Gravity.CENTER); dash.setTextColor(FocusUi.MUTED);
        times.addView(dash,new LinearLayout.LayoutParams(FocusUi.dp(this,24),-2));
        timeField(times,window.end<window.start?"结束时间 · 次日":"结束时间",DailySchedule.clock(window.end),index,window.enabled);
        LinearLayout footer = new LinearLayout(this); footer.setGravity(Gravity.CENTER_VERTICAL); card.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        TextView repeat = new TextView(this); repeat.setText("每天重复 · " + (!window.enabled?"此段已关闭":ScheduleManager.enabled(this)?"随计划执行":"待开启每日计划"));
        repeat.setTextColor(FocusUi.MUTED); repeat.setTextSize(12); footer.addView(repeat,new LinearLayout.LayoutParams(0,-2,1));
        TextView delete = new TextView(this); delete.setText("删除"); delete.setTextSize(14); delete.setGravity(Gravity.CENTER);
        delete.setTextColor(FocusUi.GREEN); delete.setContentDescription("删除时段"+(index+1));
        footer.addView(delete,new LinearLayout.LayoutParams(FocusUi.dp(this,52),FocusUi.dp(this,48)));
        delete.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("删除这个时段？").setMessage(window.label()+" · 每天")
            .setPositiveButton("删除",(d,which) -> {
                DailySchedule current = ScheduleManager.schedule(this);
                if (!current.encode().equals(s.encode())) { toast("时段已变化，请重新操作"); build(); return; }
                ArrayList<DailySchedule.Window> list = new ArrayList<>(current.windows); list.remove(index);
                save(new DailySchedule(list),ScheduleManager.enabled(this));
            }).setNegativeButton("取消",null).show());
    }
    private void timeField(LinearLayout row,String title,String value,int index,boolean enabled) {
        Button field = new Button(this); field.setText(title+"\n"+value); field.setTextSize(16); field.setAllCaps(false);
        field.setTextColor(enabled?FocusUi.INK:FocusUi.MUTED); field.setBackground(rectangle(FocusUi.BG,Color.rgb(215,224,213)));
        field.setStateListAnimator(null); field.setPadding(FocusUi.dp(this,4),0,FocusUi.dp(this,4),0);
        row.addView(field,new LinearLayout.LayoutParams(0,FocusUi.dp(this,82),1)); field.setOnClickListener(v -> edit(index));
    }
    private GradientDrawable rectangle(int fill,int border) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(fill); shape.setCornerRadius(FocusUi.dp(this,14));
        shape.setStroke(FocusUi.dp(this,1),border); return shape;
    }
    private void edit(int index) {
        DailySchedule s = ScheduleManager.schedule(this);
        if (index < 0 && s.windows.size() >= DailySchedule.MAX_WINDOWS) { toast("最多设置8个时段"); return; }
        if (getFragmentManager().findFragmentByTag(TimeRangeEditorDialog.TAG) != null) return;
        DailySchedule.Window window = index >= 0 ? s.windows.get(index) : new DailySchedule.Window(9*60,12*60);
        TimeRangeEditorDialog.create(index,window,s.encode()).show(getFragmentManager(),TimeRangeEditorDialog.TAG);
    }
    boolean applyTimeRange(int index,int start,int end,boolean enabled,String snapshot) {
        try {
            DailySchedule s = ScheduleManager.schedule(this);
            if (!s.encode().equals(snapshot)) { toast("时段已变化，请取消后重新打开"); return false; }
            ArrayList<DailySchedule.Window> list = new ArrayList<>(s.windows);
            DailySchedule.Window window = new DailySchedule.Window(start,end,enabled);
            if (index < 0) list.add(window); else list.set(index,window);
            return save(new DailySchedule(list),ScheduleManager.enabled(this));
        } catch (IllegalArgumentException e) { toast(e.getMessage()); return false; }
    }
    private void disclosure() {
        new AlertDialog.Builder(this).setTitle("授权用途与使用范围")
            .setMessage("为了在每日时段内拦截应用，本应用通过无障碍服务接收当前窗口所属的应用标识，并显示专注覆盖页面。应用标识仅在内存中判断，不保存、不上传。\n\n不读取屏幕文字、聊天内容或密码，不截图，不模拟点击。你可随时在系统设置撤销授权；电话和系统权限设置保留。\n\n同意后，在手机无障碍设置中打开「去做正事 · 每日时段」。如果安装来源导致服务不可用，可在系统应用信息中查看是否需要先允许受限设置。")
            .setPositiveButton("同意并打开授权",(d,w)->safeOpen(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
            .setNegativeButton("暂不授权",null).show();
    }
    private boolean save(DailySchedule s, boolean enabled) {
        try { ScheduleManager.save(this,s,enabled); build(); return true; }
        catch (RuntimeException e) { toast(e.getMessage()); build(); return false; }
    }
    private void renderStatus() {
        if (status == null) return;
        String value;
        if (!ScheduleManager.enabled(this)) value = "每日自动执行：未启用";
        else if (!ScheduleManager.schedule(this).hasEnabledWindows()) value = "每日计划已开启 · 所有时段均已关闭";
        else if (!ScheduleManager.permissionEnabled(this)) value = "时段已保存 · 等待手机无障碍授权";
        else if (!ScheduleAccessibilityService.connected()) value = "时段已保存 · 服务尚未连接，请检查系统授权与后台设置";
        else {
            value = ScheduleManager.active(this) ? "● 当前处于禁用时段" : "● 每日计划已开启 · 等待下个时段";
            Instant next = ScheduleManager.schedule(this).nextChange(Instant.now(), ZoneId.systemDefault());
            if (next != null) value += "\n下次" + (ScheduleManager.active(this)?"恢复：":"开始：") + DateTimeFormatter.ofPattern("MM月dd日 HH:mm").withZone(ZoneId.systemDefault()).format(next);
        }
        if (!ScheduleManager.exactAllowed(this)) value += "\n未开启准时闹钟：后台休眠时边界可能延迟";
        if (FocusManager.hasSession(this)) value += "\n当前受管设备专注优先，结束后恢复每日计划";
        value += "\n" + ScheduleManager.prefs(this).getString("alarm_error","") + ScheduleManager.prefs(this).getString("overlay_error","");
        status.setText(value.trim());
    }
    private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
    private void safeOpen(Intent intent) { try { startActivity(intent); } catch (RuntimeException e) { toast("请在手机系统设置中找到对应权限"); } }
    @Override protected void onResume() {
        super.onResume(); FocusManager.reconcile(this);
        if (FocusManager.hasSession(this)) { FocusManager.open(this); return; }
        // Changing the launcher must not lose the old monitor's explicit enabled state.
        if (AppState.enabled(this) && !ScreenMonitorService.isRunning() && new DeviceLocker(this).isAuthorized()) {
            try { startForegroundService(new Intent(this, ScreenMonitorService.class)); }
            catch (RuntimeException e) { AppState.error(this,"系统未允许恢复三分钟息屏，请到可选功能中重新开启。"); }
        }
        ScheduleManager.changed(this); build(); handler.removeCallbacks(refresh); handler.post(refresh);
    }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
