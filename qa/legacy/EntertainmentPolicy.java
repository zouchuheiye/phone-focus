package com.phonefocus.guard;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Rules over OCR text, not a universal image classifier. Never persists input. */
public final class EntertainmentPolicy {
    private static final Set<String> FEEDS = Set.of("com.ss.android.ugc.aweme", "com.ss.android.ugc.aweme.lite",
        "com.smile.gifmaker", "com.kuaishou.nebula", "tv.danmaku.bili", "com.google.android.youtube");
    private static final String[] STUDY = {"考研", "网课", "课程", "课堂", "讲义", "练习题", "真题", "数学",
        "英语", "物理", "化学", "教程", "教学", "公开课", "lecture", "tutorial", "lesson"};
    private static final String[] GAME = {"开始游戏", "匹配对局", "排位赛", "开始匹配", "进入游戏", "段位", "战绩", "英雄选择", "出装", "对战", "胜利", "失败", "击杀", "复活"};
    private static final String[] VIDEO = {"弹幕", "倍速", "选集", "追剧", "追番", "下一集", "全集", "会员抢先看", "剧情", "综艺", "番剧"};
    private static final String[] FEED = {"推荐", "关注", "点赞", "评论", "分享", "同款", "短剧", "shorts"};
    private static final String[] LIVE = {"直播间", "送礼物", "礼物榜", "粉丝团", "主播", "连麦", "打赏"};
    private EntertainmentPolicy() { }
    private static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
    private static int hits(String text, String[] words) {
        int count=0; for (String word:words) if (text.contains(word)) count++; return count;
    }
    /** Empty means uncertain/non-entertainment. Returned reason contains no OCR text. */
    public static String classify(String packageName, String recognizedText) {
        String text=normalize(recognizedText);
        if (text.isEmpty() || hits(text,STUDY)>0) return "";
        if (hits(text,GAME)>=2) return "游戏界面特征";
        if (hits(text,LIVE)>=2) return "娱乐直播界面特征";
        if (hits(text,VIDEO)>=3) return "追剧或娱乐视频界面特征";
        if (packageName!=null && FEEDS.contains(packageName) && hits(text,FEED)>=3) return "短视频或推荐流界面特征";
        return "";
    }
}
