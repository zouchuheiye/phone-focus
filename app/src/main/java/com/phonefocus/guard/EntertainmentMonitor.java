package com.phonefocus.guard;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.SystemClock;
import android.view.Display;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

/** Opt-in screenshot -> local bundled OCR -> rules -> revalidation -> system lock. */
final class EntertainmentMonitor {
    private final ScheduleAccessibilityService service;
    private final EntertainmentCadence cadence=new EntertainmentCadence();
    private TextRecognizer recognizer;
    private boolean busy,closed;
    private long requestId;
    EntertainmentMonitor(ScheduleAccessibilityService service) { this.service=service; }
    void invalidate() { requestId++; }
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
                    Bitmap image=null,hardware=null,software=null;
                    try (HardwareBuffer buffer=result.getHardwareBuffer()) {
                        if (!current(id,revision,pkg,started)) { busy=false; if (closed) closeRecognizer(); return; }
                        hardware=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());
                        if (hardware==null) throw new IllegalStateException("No bitmap");
                        software=hardware.copy(Bitmap.Config.ARGB_8888,false);
                        if (software==null) throw new IllegalStateException("No pixels");
                        float ratio=Math.min(1f,1600f/Math.max(software.getWidth(),software.getHeight()));
                        image=Bitmap.createScaledBitmap(software,Math.max(1,Math.round(software.getWidth()*ratio)),Math.max(1,Math.round(software.getHeight()*ratio)),true);
                        if (image==software) software=null;
                        if (recognizer==null) recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
                        Bitmap owned=image;
                        recognizer.process(InputImage.fromBitmap(owned,0))
                            .addOnSuccessListener(text -> {
                                if (!current(id,revision,pkg,started)) return;
                                String reason=EntertainmentPolicy.classify(pkg,text.getText());
                                if (reason.isEmpty()) { report("未确认娱乐内容，本轮未锁屏"); return; }
                                // App/window, power, call state and consent must still match at action time.
                                if (!current(id,revision,pkg,started)) return;
                                try {
                                    boolean accepted=service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
                                    report(accepted?"识别到"+reason+"，已发送锁屏指令":"识别到娱乐，但系统未接受锁屏指令");
                                } catch (RuntimeException error) { report("识别到娱乐，但系统拒绝锁屏；请检查无障碍授权"); }
                            })
                            .addOnFailureListener(error -> { if (current(id,revision,pkg,started)) report("本地文字识别失败，本轮未锁屏"); })
                            .addOnCompleteListener(task -> { owned.recycle(); busy=false; if (closed) closeRecognizer(); });
                        image=null; // Ownership is held until the asynchronous OCR task finishes.
                    } catch (RuntimeException error) {
                        busy=false; if (current(id,revision,pkg,started)) report("截图处理失败，本轮未锁屏");
                        if (closed) closeRecognizer();
                    } finally {
                        if (image!=null) image.recycle();
                        if (software!=null) software.recycle();
                        if (hardware!=null) hardware.recycle();
                    }
                }
                @Override public void onFailure(int code) {
                    busy=false;
                    if (current(id,revision,pkg,started)) report("截图失败（系统代码"+code+"），本轮未锁屏；请检查截图能力或重新启用无障碍");
                    if (closed) closeRecognizer();
                }
            });
        } catch (RuntimeException error) { busy=false; report("系统拒绝截图，本轮未锁屏"); }
    }
    private boolean current(long id,long revision,String pkg,long started) {
        return !closed && id==requestId && EntertainmentCadence.current(EntertainmentSettings.enabled(service),
            service.entertainmentEligible(),revision,service.foregroundRevision(),pkg,service.foregroundPackage(),SystemClock.elapsedRealtime()-started);
    }
    private void closeRecognizer() { if (recognizer!=null) { recognizer.close(); recognizer=null; } }
    void close() { closed=true; invalidate(); if (!busy) closeRecognizer(); }
}
