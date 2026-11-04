package com.phonefocus.guard;

public final class EntertainmentGuardTest {
    private static int count;
    private static void check(boolean value,String why) { count++; if (!value) throw new AssertionError(why); }
    private static void yes(String pkg,String text) { check(!EntertainmentPolicy.classify(pkg,text).isEmpty(),"expected entertainment: "+text); }
    private static void no(String pkg,String text) { check(EntertainmentPolicy.classify(pkg,text).isEmpty(),"expected uncertain/study: "+text); }
    public static void main(String[] args) {
        String feed="com.ss.android.ugc.aweme",bili="tv.danmaku.bili";
        yes(feed,"推 荐 关注 点赞 评论 分享");
        yes(bili,"推荐 关注 分享");
        yes("game.sample","开始游戏 排位赛");
        yes("game.sample","胜利 战绩");
        yes("browser.sample","直播间 送礼物 粉丝团");
        yes("browser.sample","选集 下一集 追剧 倍速");
        no(feed,"数学 考研 真题 推荐 关注 点赞 评论");
        no(bili,"Tutorial 推荐 关注 分享");
        no("browser.sample","公开课 弹幕 倍速 选集");
        no("chat.sample","推荐 关注 分享");
        no("game.sample","开始游戏");
        no("unknown.sample","纯图片和无文字游戏可能漏判");
        no(feed,"");no(feed,null);no(null,"推荐 关注 分享");
        yes(feed,"ＳＨＯＲＴＳ 关注 评论");
        EntertainmentCadence c=new EntertainmentCadence();long interval=EntertainmentCadence.INTERVAL_MS;
        check(!c.poll(0,false),"disabled");check(!c.poll(100,true),"first enable must wait");
        check(!c.poll(interval+99,true),"before deadline");check(c.poll(interval+100,true),"exact ten minutes");
        check(!c.poll(interval+100,true),"one event per interval");
        check(c.poll(20*interval,true),"late tick only one sample");
        check(!c.poll(20*interval+1,true),"no catch-up burst");
        check(!c.poll(20*interval+2,false),"turn off");check(!c.poll(21*interval,true),"reenable waits");
        check(!c.poll(22*interval-1,true),"reenable before due");check(c.poll(22*interval,true),"reenable due");
        check(EntertainmentCadence.current(true,true,3,3,feed,feed,14999),"fresh result");
        check(!EntertainmentCadence.current(false,true,3,3,feed,feed,1),"consent withdrawn");
        check(!EntertainmentCadence.current(true,false,3,3,feed,feed,1),"call/screen off/locked/protected");
        check(!EntertainmentCadence.current(true,true,3,4,feed,feed,1),"same app changed window");
        check(!EntertainmentCadence.current(true,true,3,3,feed,bili,1),"changed app");
        check(!EntertainmentCadence.current(true,true,3,3,feed,feed,15000),"stale at cutoff");
        check(!EntertainmentCadence.current(true,true,3,3,feed,feed,-1),"invalid age");
        check(!EntertainmentCadence.current(true,true,3,3,null,null,1),"unknown package");
        System.out.println("Entertainment guard: "+count+" focused rule/cadence/stale-result checks passed. Not an OCR accuracy or Android runtime test.");
    }
}
