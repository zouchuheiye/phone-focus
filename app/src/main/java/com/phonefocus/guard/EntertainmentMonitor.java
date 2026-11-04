package com.phonefocus.guard;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.view.Display;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Screenshot -> Agnes -> strict verdict -> current-window check -> persistent focus overlay. */
final class EntertainmentMonitor {
    private final ScheduleAccessibilityService service;
    private final EntertainmentCadence cadence=new EntertainmentCadence();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AgnesClient client=new AgnesClient();
    private boolean busy;
    private volatile boolean closed;
    private volatile long requestId;
    EntertainmentMonitor(ScheduleAccessibilityService service) { this.service=service; }
    void invalidate() { requestId++; client.cancel(); }
    void poll() {
        if (closed) return;
        boolean on=EntertainmentSettings.enabled(service);
        if (!cadence.poll(SystemClock.elapsedRealtime(),on)) { if (!on) invalidate(); return; }
        if (busy) { report("上次识别尚未结束，本轮跳过"); return; }
        if (!service.entertainmentEligible()) { report("未使用手机或处于通话/保护界面，本轮未截图"); return; }
        capture();
    }
    private void report(String message) { if (!closed) EntertainmentSettings.result(service,message); }
    @android.annotation.TargetApi(30)
    private void capture() {
        busy=true;
        long id=++requestId,revision=service.foregroundRevision(),started=SystemClock.elapsedRealtime();
        String pkg=service.foregroundPackage();
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY,service.getMainExecutor(),new AccessibilityService.TakeScreenshotCallback() {
                @Override public void onSuccess(AccessibilityService.ScreenshotResult result) {
                    Bitmap hardware=null,image=null;
                    try (HardwareBuffer buffer=result.getHardwareBuffer()) {
                        if (!current(id,revision,pkg,started)) { busy=false; return; }
                        hardware=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());
                        if (hardware==null) throw new IllegalStateException("No bitmap");
                        image=hardware.copy(Bitmap.Config.ARGB_8888,false);
                        if (image==null) throw new IllegalStateException("No pixels");
                        AgnesConfig config=AgnesConfig.read(service);
                        Bitmap owned=image;
                        worker.execute(() -> analyze(owned,config,id,revision,pkg,started));
                        image=null;
                    } catch (Exception error) {
                        busy=false; if (current(id,revision,pkg,started)) report("截图或密钥读取失败，本轮未上传、未锁屏；请检查Agnes配置");
                    } finally { if (image!=null) image.recycle(); if (hardware!=null) hardware.recycle(); }
                }
                @Override public void onFailure(int code) {
                    busy=false;
                    if (current(id,revision,pkg,started)) report("截图失败（系统代码"+code+"），本轮未上传、未锁屏；请检查截图能力或重新启用无障碍");
                }
            });
        } catch (RuntimeException error) { busy=false; report("系统拒绝截图，本轮未锁屏"); }
    }
    private void analyze(Bitmap original,AgnesConfig config,long id,long revision,String pkg,long started) {
        Bitmap scaled=null;
        AgnesProtocol.Verdict verdict=null;
        boolean failed=false;
        try {
            if (closed || id!=requestId) return;
            float ratio=Math.min(1f,1280f/Math.max(original.getWidth(),original.getHeight()));
            scaled=Bitmap.createScaledBitmap(original,Math.max(1,Math.round(original.getWidth()*ratio)),Math.max(1,Math.round(original.getHeight()*ratio)),true);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            if (!scaled.compress(Bitmap.CompressFormat.JPEG,75,bytes) || bytes.size()>1024*1024) throw new IllegalStateException("Image encoding failed");
            String image=Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);
            verdict=client.classify(config.key,config.model,pkg,image,() -> !closed && id==requestId);
        } catch (Exception error) { failed=true; }
        finally {
            if (scaled!=null && scaled!=original) scaled.recycle(); original.recycle();
            AgnesProtocol.Verdict value=verdict;boolean error=failed;
            main.post(() -> {
                busy=false;
                if (!current(id,revision,pkg,started)) return;
                if (error || value==null) { report("Agnes请求失败、超时或结果格式无效，本轮未禁用；请检查网络、密钥和额度");return; }
                if (!value.shouldBlock()) { report("Agnes未明确确认娱乐，本轮未禁用");return; }
                try {
                    EntertainmentBlock.start(service);
                    report("Agnes明确判断娱乐，已进入"+EntertainmentSettings.blockMinutes(service)+"分钟禁用模式");
                } catch (RuntimeException failure) { report("Agnes判断娱乐，但禁用状态保存失败，本轮未进入禁用"); }
            });
        }
    }
    private boolean current(long id,long revision,String pkg,long started) {
        return !closed && id==requestId && EntertainmentCadence.current(EntertainmentSettings.enabled(service),
            service.entertainmentEligible(),revision,service.foregroundRevision(),pkg,service.foregroundPackage(),SystemClock.elapsedRealtime()-started);
    }
    void close() { closed=true;invalidate();worker.shutdownNow();client.close(); }
}
