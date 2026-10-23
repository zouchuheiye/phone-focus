package com.phonefocus.guard;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

final class FocusUi {
    static final int BG=Color.rgb(245,244,239), INK=Color.rgb(35,51,42), GREEN=Color.rgb(66,102,83), MUTED=Color.rgb(106,119,110);
    private FocusUi() {}
    static int dp(Activity a,float value) { return Math.round(value*a.getResources().getDisplayMetrics().density); }
    static LinearLayout page(Activity a) {
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(a); scroll.setBackgroundColor(BG); scroll.setFillViewport(true);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            if (Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout content=new LinearLayout(a); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(a,24),dp(a,32),dp(a,24),dp(a,28)); scroll.addView(content); a.setContentView(scroll); return content;
    }
    static TextView text(Activity a,LinearLayout page,String value,int size,int color) {
        TextView t=new TextView(a); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(dp(a,4),1);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(a,12),0,dp(a,10));page.addView(t,p); return t;
    }
    static Button button(Activity a,LinearLayout page,String label,boolean primary,Runnable action) {
        Button b=new Button(a); b.setText(label); b.setTextSize(16);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:GREEN);
        GradientDrawable shape=new GradientDrawable();shape.setColor(primary?GREEN:Color.WHITE);shape.setCornerRadius(dp(a,15));b.setBackground(shape);b.setStateListAnimator(null);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,54));p.setMargins(0,dp(a,10),0,dp(a,8));page.addView(b,p);b.setOnClickListener(v->action.run());return b;
    }
    static String duration(long millis) {
        long seconds=(Math.max(0,millis)+999)/1000;
        return String.format(Locale.CHINA,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60);
    }
}
