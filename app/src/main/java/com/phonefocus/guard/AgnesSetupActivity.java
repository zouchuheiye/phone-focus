package com.phonefocus.guard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** User-owned credential entry/import; no credential is bundled with the public APK. */
public final class AgnesSetupActivity extends Activity {
    private static final int IMPORT=1501;
    private EditText key;
    private Spinner models;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout page=FocusUi.page(this);
        FocusUi.text(this,page,"Agnes 视觉模型",28,FocusUi.INK);
        FocusUi.text(this,page,"将截图交给大模型理解，判断学习、工作、娱乐或必要操作。",16,FocusUi.GREEN);
        FocusUi.text(this,page,"接口固定为 apihub.agnes-ai.com，仅HTTPS。开启检查后，截图和当前应用标识会发送给Agnes；可能包含私人信息，平台的数据处理和计费按其政策。应用本身不保存画面或模型原文。",14,FocusUi.MUTED);
        FocusUi.text(this,page,AgnesConfig.present(this)?"已保存一份加密配置。输入新密钥或导入可替换；不会回显旧密钥。":"尚未配置API密钥。公开安装包不共享开发者账户，请输入个人密钥或导入私有配置。",14,FocusUi.GREEN);
        key=new EditText(this);key.setHint("Agnes API Key");key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setSaveEnabled(false);key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        page.addView(key,new LinearLayout.LayoutParams(-1,FocusUi.dp(this,56)));
        models=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"agnes-2.5-flash","agnes-3.0-flash"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);models.setAdapter(adapter);page.addView(models);
        FocusUi.text(this,page,"默认2.5 Flash已通过合成画面接口验证。模型可能误判，置信度是模型自报值，不等于实测准确率。",13,FocusUi.MUTED);
        FocusUi.button(this,page,"保存密钥与模型",true,() -> {
            try { AgnesConfig.save(this,key.getText().toString(),models.getSelectedItem().toString()); key.setText("");toast("配置已加密保存。返回首页，阅读云端用途并开启检查。");finish(); }
            catch (Exception error) { toast("保存失败，请检查密钥格式和设备安全存储；未开启检查。"); }
        });
        FocusUi.button(this,page,"导入私有配置文件",false,() -> {
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain"});
            try { startActivityForResult(intent,IMPORT); } catch (RuntimeException error) { toast("系统文件选择器不可用，请手动输入密钥"); }
        });
        FocusUi.button(this,page,"删除本机密钥并暂停检查",false,() -> {
            new android.app.AlertDialog.Builder(this).setTitle("删除Agnes配置？").setMessage("会停止云端截图检查并删除本机加密密钥。每日时段保持。")
                .setPositiveButton("删除",(d,w) -> { AgnesConfig.clear(this);key.setText("");toast("已删除并暂停");finish(); }).setNegativeButton("取消",null).show();
        });
        FocusUi.text(this,page,"密钥由Android Keystore加密保存，应用备份已关闭。私有配置文件包含明文密钥，请勿上传仓库或分享；导入保存后可从下载目录删除该文件。不会自行打开检查开关。",13,FocusUi.MUTED);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request!=IMPORT || result!=RESULT_OK || data==null || data.getData()==null) return;
        try (InputStream input=getContentResolver().openInputStream(data.getData())) {
            if (input==null) throw new IllegalArgumentException("No file");
            byte[] raw=new byte[8193];int used=0,n;
            while (used<raw.length && (n=input.read(raw,used,raw.length-used))!=-1) used+=n;
            if (used>8192) throw new IllegalArgumentException("File too large");
            JSONObject value=new JSONObject(new String(raw,0,used,StandardCharsets.UTF_8));
            String base=value.optString("base_url","https://apihub.agnes-ai.com/v1");
            if (!"https://apihub.agnes-ai.com/v1".equals(base.replaceAll("/+$",""))) throw new IllegalArgumentException("Wrong endpoint");
            String model=value.optString("model",AgnesProtocol.DEFAULT_MODEL);
            if (!AgnesProtocol.validModel(model)) throw new IllegalArgumentException("Unsupported model");
            key.setText(value.getString("api_key"));models.setSelection(model.equals(AgnesProtocol.DEFAULT_MODEL)?0:1);
            toast("已读取私有配置，请点保存；尚未开启上传检查。");
        } catch (Exception error) { key.setText("");toast("导入失败，请检查Agnes配置格式；文件内容不会写入日志。"); }
    }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
}
