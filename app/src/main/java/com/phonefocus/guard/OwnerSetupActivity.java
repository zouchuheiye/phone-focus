package com.phonefocus.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

public final class OwnerSetupActivity extends Activity {
    private TextView capability, selectedTime;
    private Button start, test;
    private long duration=25*60_000L;
    private long chosenWallEnd;
    private static final String COMMAND="adb shell dpm set-device-owner com.phonefocus.guard/.LockAdminReceiver";
    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        if(savedState!=null){duration=savedState.getLong("duration",duration);chosenWallEnd=savedState.getLong("chosen_end",0);}
        LinearLayout page=FocusUi.page(this);
        FocusUi.text(this,page,"设备管理与强制专注",27,FocusUi.INK).setTypeface(null,Typeface.BOLD);
        FocusUi.text(this,page,"设定结束时间，在系统管理模式下限制切换到其他应用，到时解除限制。",16,FocusUi.MUTED);
        capability=FocusUi.text(this,page,"检查管理权限……",16,FocusUi.GREEN);
        FocusUi.button(this,page,"查看权限升级步骤",false,()->new AlertDialog.Builder(this)
            .setTitle("配置设备所有者")
            .setMessage("此权限需要通过电脑ADB或正式设备部署流程配置，应用不能自行授予。\n\n1. 手机启用开发者选项和USB调试，并在手机上确认电脑连接。\n2. 确认手机没有账号、工作资料或其他用户，且没有其他设备所有者。\n3. 在电脑执行下方命令，成功后返回此页检查。\n\n"+COMMAND+"\n\n若系统拒绝，请保留错误并停止，不要自行清空手机。普通设备管理员授权不能代替设备所有者。")
            .setPositiveButton("复制命令",(d,w)->{
                getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("设备所有者配置命令",COMMAND));
                Toast.makeText(this,"已复制，请在已确认连接的电脑执行。",Toast.LENGTH_LONG).show();
            }).setNegativeButton("关闭",null).show());
        FocusUi.button(this,page,"允许准时结束权限",false,()->{
            if(Build.VERSION.SDK_INT>=31) {
                try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}
                catch(RuntimeException missing){Toast.makeText(this,"请在系统设置中允许闹钟和提醒。",Toast.LENGTH_LONG).show();}
            }else Toast.makeText(this,"此系统版本无需单独授予准时结束权限。",Toast.LENGTH_SHORT).show();
        });
        selectedTime=FocusUi.text(this,page,"",22,FocusUi.INK);
        for(int minutes:new int[]{15,25,45,60})FocusUi.button(this,page,minutes+" 分钟",false,()->{duration=minutes*60_000L;chosenWallEnd=0;render();});
        FocusUi.button(this,page,"指定结束时间",false,()->{
            Calendar now=Calendar.getInstance();
            new TimePickerDialog(this,(picker,hour,minute)->{
                Calendar end=Calendar.getInstance();end.set(Calendar.HOUR_OF_DAY,hour);end.set(Calendar.MINUTE,minute);end.set(Calendar.SECOND,0);end.set(Calendar.MILLISECOND,0);
                if(end.getTimeInMillis()<=System.currentTimeMillis())end.add(Calendar.DAY_OF_MONTH,1);
                chosenWallEnd=end.getTimeInMillis();render();
            },now.get(Calendar.HOUR_OF_DAY),now.get(Calendar.MINUTE),true).show();
        });
        test=FocusUi.button(this,page,"先试用 30 秒强制专注",false,()->confirm(30_000L));
        start=FocusUi.button(this,page,"确认并开始强制专注",true,()->confirm(chosenWallEnd>0?chosenWallEnd-System.currentTimeMillis():duration));
        FocusUi.text(this,page,"专注期间普通返回、主屏幕与任务切换受限制；系统电话入口保留，可息屏和关机。原系统密码保持不变。\n\n开始前会显示一次紧急恢复码，请保存在手机之外。设备所有者配置、重启恢复和系统电话行为需在你的手机验证。",14,FocusUi.MUTED);
        FocusUi.button(this,page,"解除设备所有者管理",false,this::removeOwner);
        FocusUi.button(this,page,"返回",false,this::finish);
        render();
    }
    private void render(){
        String unavailable=FocusManager.availability(this);
        capability.setText(unavailable.isEmpty()?"设备所有者已就绪，准时结束权限可用。":unavailable);
        boolean ready=unavailable.isEmpty()&&!FocusManager.hasSession(this);
        start.setEnabled(ready);test.setEnabled(ready);start.setAlpha(ready?1:.45f);test.setAlpha(ready?1:.45f);
        long selected=chosenWallEnd>0?chosenWallEnd-System.currentTimeMillis():duration;
        selectedTime.setText("本次专注 "+FocusUi.duration(selected)+"\n预计结束："+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(System.currentTimeMillis()+Math.max(0,selected))));
    }
    private void confirm(long milliseconds){
        if(FocusManager.hasSession(this)){FocusManager.open(this);return;}
        if(milliseconds<1000||milliseconds>FocusWindow.MAX_DURATION_MS){Toast.makeText(this,"结束时间须在未来24小时内。",Toast.LENGTH_LONG).show();return;}
        final String code=RecoveryCode.generate();
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(36,12,36,8);
        TextView value=new TextView(this);value.setText(code);value.setTextSize(19);value.setTypeface(Typeface.MONOSPACE);box.addView(value);
        CheckBox saved=new CheckBox(this);saved.setText("我已将恢复码保存在另一台设备、纸上或交给可信的人");box.addView(saved);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("开始后，需等到时间结束")
            .setMessage("这次恢复码只在开始前显示。请存放在手机之外；只有输入恢复码才能紧急提前解除。原系统锁屏密码不会改变。")
            .setView(box).setPositiveButton("已保存，开始专注",null).setNegativeButton("暂不开始",null).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            saved.setOnCheckedChangeListener((b,checked)->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(checked));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try{
                    long actual=chosenWallEnd>0&&milliseconds!=30_000L?chosenWallEnd-System.currentTimeMillis():milliseconds;
                    FocusManager.start(this,actual,code);dialog.dismiss();finish();
                }catch(RuntimeException failure){
                    dialog.dismiss();Toast.makeText(this,"启动未完成："+failure.getMessage()+"。请检查管理状态和恢复提示。",Toast.LENGTH_LONG).show();
                    if(FocusManager.hasSession(this)){FocusManager.open(this);finish();}else render();
                }
            });
        });
        dialog.show();
    }
    @SuppressWarnings("deprecation")
    private void removeOwner(){
        if(FocusManager.hasSession(this)){FocusManager.open(this);return;}
        if(!FocusManager.isOwner(this)){Toast.makeText(this,"当前应用不是设备所有者。",Toast.LENGTH_SHORT).show();return;}
        new AlertDialog.Builder(this).setTitle("解除设备管理？").setMessage("将移除本应用的设备所有者身份，之后无法启动强制专注。普通息屏功能可以重新授权使用。")
            .setPositiveButton("解除管理",(d,w)->{
                if(FocusManager.hasSession(this))return;
                try{getSystemService(DevicePolicyManager.class).clearDeviceOwnerApp(getPackageName());render();}
                catch(RuntimeException denied){Toast.makeText(this,"系统未允许解除，请保留提示并查看配置说明。",Toast.LENGTH_LONG).show();}
            }).setNegativeButton("保留",null).show();
    }
    @Override protected void onResume(){
        super.onResume();FocusManager.reconcile(this);
        if(FocusManager.hasSession(this)){FocusManager.open(this);finish();return;}
        if(capability!=null)render();
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putLong("duration",duration);state.putLong("chosen_end",chosenWallEnd);super.onSaveInstanceState(state);}
}
