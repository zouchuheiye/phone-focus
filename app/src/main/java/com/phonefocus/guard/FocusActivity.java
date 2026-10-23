package com.phonefocus.guard;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class FocusActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView timer, state;
    private final Runnable tick=new Runnable() {
        @Override public void run() {
            if (!FocusManager.hasSession(FocusActivity.this)) { leave();return; }
            if (FocusManager.ACTIVE.equals(FocusManager.phase(FocusActivity.this)) && FocusManager.remaining(FocusActivity.this)<=0) {
                FocusManager.release(FocusActivity.this,"专注时间已到，使用限制已解除。");
                if (!FocusManager.hasSession(FocusActivity.this)) { leave();return; }
            }
            timer.setText(FocusUi.duration(FocusManager.remaining(FocusActivity.this)));
            String error=FocusManager.prefs(FocusActivity.this).getString("error","");
            boolean locked=getSystemService(ActivityManager.class).getLockTaskModeState()==ActivityManager.LOCK_TASK_MODE_LOCKED;
            state.setText(!error.isEmpty()?error:locked?"系统强制专注已生效\n到时自动恢复普通使用。":"正在进入系统专注模式……");
            handler.postDelayed(this,500);
        }
    };
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout page=FocusUi.page(this);
        FocusUi.text(this,page,"去做正事",31,FocusUi.INK).setTypeface(null,Typeface.BOLD);
        FocusUi.text(this,page,"这段时间，留给眼前。",19,FocusUi.MUTED);
        timer=FocusUi.text(this,page,"00:00:00",48,FocusUi.GREEN);timer.setGravity(Gravity.CENTER);timer.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        state=FocusUi.text(this,page,"正在检查专注状态",16,FocusUi.MUTED);
        FocusUi.text(this,page,"等待期间可熄灭屏幕。\n到时解除应用限制，原来的系统密码保持不变。",16,FocusUi.INK);
        FocusUi.button(this,page,"紧急拨号",false,this::emergency);
        FocusUi.button(this,page,"使用手机外保存的恢复码",false,this::recover);
        FocusUi.button(this,page,"重试策略恢复",false,()->{
            if (FocusManager.RESTORING.equals(FocusManager.phase(this))) FocusManager.release(this,"策略已恢复。");
            else Toast.makeText(this,"专注进行中，不能提前结束。",Toast.LENGTH_SHORT).show();
        });
        FocusUi.text(this,page,"正常结束前没有退出按钮。紧急恢复需使用开始前保存在手机之外的恢复码。",13,FocusUi.MUTED);
        if (Build.VERSION.SDK_INT>=33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->{if(!FocusManager.hasSession(this))leave();});
    }
    @Override protected void onResume() {
        super.onResume();
        FocusManager.reconcile(this);
        if (!FocusManager.hasSession(this)) { leave();return; }
        if (FocusManager.ACTIVE.equals(FocusManager.phase(this)) && !getSystemService(KeyguardManager.class).isKeyguardLocked()) {
            try {
                if (!FocusManager.isOwner(this) || !getSystemService(android.app.admin.DevicePolicyManager.class).isLockTaskPermitted(getPackageName()))
                    throw new SecurityException("Lock task not allowlisted");
                if (getSystemService(ActivityManager.class).getLockTaskModeState()!=ActivityManager.LOCK_TASK_MODE_LOCKED) startLockTask();
                handler.postDelayed(()->{
                    if(hasWindowFocus() && FocusManager.ACTIVE.equals(FocusManager.phase(this))
                        && getSystemService(ActivityManager.class).getLockTaskModeState()!=ActivityManager.LOCK_TASK_MODE_LOCKED) {
                        FocusManager.release(this,"系统未确认强制锁定，专注已取消。");
                        Toast.makeText(this,"系统未进入强制模式，已尝试恢复原设置。",Toast.LENGTH_LONG).show();
                        if(!FocusManager.hasSession(this))leave();
                    }
                },1000);
                FocusManager.ensureService(this);
            } catch (RuntimeException failure) {
                FocusManager.release(this,"系统没有进入强制模式，本次专注已取消。");
                Toast.makeText(this,"系统未允许强制锁定，已尝试恢复原设置。",Toast.LENGTH_LONG).show();
                if(!FocusManager.hasSession(this)){leave();return;}
            }
        }
        handler.removeCallbacks(tick);handler.post(tick);
    }
    @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
    @Override public void onBackPressed(){if(!FocusManager.hasSession(this))leave();}
    private void emergency() {
        try{startActivity(FocusManager.dialer(this));}
        catch(RuntimeException unavailable){Toast.makeText(this,"无法打开紧急拨号。可使用系统锁屏上的紧急呼叫。",Toast.LENGTH_LONG).show();}
    }
    private void recover() {
        EditText input=new EditText(this);input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setHint("输入开始前保存的恢复码");
        new AlertDialog.Builder(this).setTitle("紧急恢复").setMessage("请输入保存在手机之外的恢复码。此操作将提前解除专注限制。")
            .setView(input).setPositiveButton("验证并恢复",(d,w)->{
                String code=input.getText().toString();
                if(FocusManager.recover(this,code))leave();
                else if(RecoveryCode.matches(code,FocusManager.prefs(this).getString("recovery_hash",""))){
                    new AlertDialog.Builder(this).setTitle("恢复码已验证，策略仍待恢复")
                        .setMessage("可以再次尝试恢复；若系统持续拒绝，可紧急解除本应用的设备所有者管理。解除后需通过电脑重新配置才能使用强制专注。")
                        .setPositiveButton("解除管理并恢复使用",(again,which)->{
                            if(FocusManager.emergencyRemoveOwner(this,code))leave();
                            else Toast.makeText(this,"系统仍未允许恢复，请保留提示并通过已授权电脑检查设备管理状态。",Toast.LENGTH_LONG).show();
                        }).setNegativeButton("稍后重试",null).show();
                }else Toast.makeText(this,"恢复码不正确。",Toast.LENGTH_LONG).show();
            }).setNegativeButton("继续专注",null).show();
    }
    private void leave(){
        if(FocusManager.hasSession(this))return;
        try{stopLockTask();}catch(RuntimeException ignored){}
        startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();
    }
}
