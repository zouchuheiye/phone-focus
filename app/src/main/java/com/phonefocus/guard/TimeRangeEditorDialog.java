package com.phonefocus.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Two rectangular hour/minute wheel selectors, with no clock-face picker. */
public final class TimeRangeEditorDialog extends DialogFragment {
    static final String TAG = "daily_time_range_editor";
    private NumberPicker startHour, startMinute, endHour, endMinute;
    private Switch enabled;
    private TextView preview, endLabel;

    static TimeRangeEditorDialog create(int index, DailySchedule.Window window, String snapshot) {
        TimeRangeEditorDialog dialog = new TimeRangeEditorDialog();
        Bundle args = new Bundle(); args.putInt("index",index); args.putInt("start",window.start);
        args.putInt("end",window.end); args.putBoolean("enabled",window.enabled); args.putString("snapshot",snapshot);
        dialog.setArguments(args); return dialog;
    }
    @Override public Dialog onCreateDialog(Bundle saved) {
        Activity a = getActivity(); Bundle draft = saved == null ? getArguments() : saved;
        int start = draft.getInt("start"), end = draft.getInt("end");
        ScrollView scroll = new ScrollView(a);
        LinearLayout content = new LinearLayout(a); content.setOrientation(LinearLayout.VERTICAL);
        int pad = FocusUi.dp(a,12); content.setPadding(pad,pad,pad,pad); scroll.addView(content);
        FocusUi.text(a,content,"上下滑动数字选择时间 · 24小时制",14,FocusUi.MUTED);

        LinearLayout row = new LinearLayout(a); row.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(row,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout from = box(a,row), to;
        TextView dash = new TextView(a); dash.setText("—"); dash.setGravity(Gravity.CENTER); dash.setTextColor(FocusUi.MUTED);
        row.addView(dash,new LinearLayout.LayoutParams(FocusUi.dp(a,20),-2));
        to = box(a,row);
        heading(a,from,"开始时间"); endLabel = heading(a,to,"结束时间");
        NumberPicker[] first = clock(a,from,start,"开始"); startHour = first[0]; startMinute = first[1];
        NumberPicker[] second = clock(a,to,end,"结束"); endHour = second[0]; endMinute = second[1];
        NumberPicker.OnValueChangeListener changed = (picker,oldValue,newValue) -> updatePreview();
        for (NumberPicker picker : new NumberPicker[]{startHour,startMinute,endHour,endMinute}) picker.setOnValueChangedListener(changed);

        preview = FocusUi.text(a,content,"",16,FocusUi.GREEN);
        enabled = new Switch(a); enabled.setText("启用这个时段"); enabled.setTextSize(15);
        enabled.setTextColor(FocusUi.INK); enabled.setChecked(draft.getBoolean("enabled",true));
        content.addView(enabled,new LinearLayout.LayoutParams(-1,FocusUi.dp(a,52)));
        FocusUi.text(a,content,"每天重复。结束时间早于开始时间，表示次日结束。",13,FocusUi.MUTED);
        updatePreview();
        AlertDialog dialog = new AlertDialog.Builder(a).setTitle(getArguments().getInt("index") < 0 ? "添加禁用时段" : "修改禁用时段")
            .setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            ScheduleSetupActivity activity = (ScheduleSetupActivity)getActivity();
            if (activity != null && activity.applyTimeRange(getArguments().getInt("index"),start(),end(),enabled.isChecked(),getArguments().getString("snapshot"))) dismiss();
        }));
        return dialog;
    }
    private LinearLayout box(Activity a,LinearLayout row) {
        LinearLayout panel = new LinearLayout(a); panel.setOrientation(LinearLayout.VERTICAL); panel.setGravity(Gravity.CENTER);
        int p = FocusUi.dp(a,4); panel.setPadding(p,p,p,p);
        GradientDrawable shape = new GradientDrawable(); shape.setColor(FocusUi.BG); shape.setCornerRadius(FocusUi.dp(a,12));
        shape.setStroke(FocusUi.dp(a,1),FocusUi.GREEN); panel.setBackground(shape);
        row.addView(panel,new LinearLayout.LayoutParams(0,-2,1)); return panel;
    }
    private TextView heading(Activity a,LinearLayout panel,String label) {
        TextView text = new TextView(a); text.setText(label); text.setTextSize(14); text.setTextColor(FocusUi.GREEN);
        text.setGravity(Gravity.CENTER); text.setMinimumHeight(FocusUi.dp(a,36));
        panel.addView(text,new LinearLayout.LayoutParams(-1,-2)); return text;
    }
    private NumberPicker[] clock(Activity a,LinearLayout panel,int minute,String prefix) {
        LinearLayout digits = new LinearLayout(a); digits.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(digits,new LinearLayout.LayoutParams(-1,-2));
        NumberPicker hour = picker(a,23,minute/60,prefix+"小时");
        NumberPicker minutes = picker(a,59,minute%60,prefix+"分钟");
        digits.addView(hour,new LinearLayout.LayoutParams(0,FocusUi.dp(a,132),1));
        TextView colon = new TextView(a); colon.setText(":"); colon.setTextSize(18); colon.setGravity(Gravity.CENTER);
        digits.addView(colon,new LinearLayout.LayoutParams(FocusUi.dp(a,8),-2));
        digits.addView(minutes,new LinearLayout.LayoutParams(0,FocusUi.dp(a,132),1));
        return new NumberPicker[]{hour,minutes};
    }
    private NumberPicker picker(Activity a,int max,int value,String label) {
        NumberPicker picker = new NumberPicker(a); picker.setMinValue(0); picker.setMaxValue(max);
        String[] values = new String[max+1];
        for (int i=0;i<=max;i++) values[i] = (i<10?"0":"")+i;
        picker.setDisplayedValues(values); picker.setValue(value); picker.setWrapSelectorWheel(true);
        picker.setMinimumWidth(0); picker.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        picker.setContentDescription(label); return picker;
    }
    private int start() { return startHour.getValue()*60+startMinute.getValue(); }
    private int end() { return endHour.getValue()*60+endMinute.getValue(); }
    private void updatePreview() {
        if (preview == null) return;
        int from = start(), to = end();
        endLabel.setText(to < from ? "结束时间 · 次日" : "结束时间");
        preview.setText(from == to ? "开始和结束时间不能相同" : DailySchedule.clock(from)+" — "+(to<from?"次日 ":"")+DailySchedule.clock(to)+" · 每天");
    }
    @Override public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (startHour != null) { out.putInt("start",start()); out.putInt("end",end()); out.putBoolean("enabled",enabled.isChecked()); }
    }
}
