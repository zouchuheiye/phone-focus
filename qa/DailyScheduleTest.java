package com.phonefocus.guard;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

public final class DailyScheduleTest {
    private static int checks;
    private static final ZoneId SH = ZoneId.of("Asia/Shanghai");
    private static void check(boolean yes,String message) { checks++; if (!yes) throw new AssertionError(message); }
    private static DailySchedule plan(int... endpoints) {
        java.util.ArrayList<DailySchedule.Window> windows = new java.util.ArrayList<>();
        for (int n=0;n<endpoints.length;n+=2) windows.add(new DailySchedule.Window(endpoints[n],endpoints[n+1]));
        return new DailySchedule(windows);
    }
    private static Instant at(String value) { return java.time.LocalDateTime.parse(value).atZone(SH).toInstant(); }
    private static void change(DailySchedule s,String from,String expected) {
        check(at(expected).equals(s.nextChange(at(from),SH)),"next change from "+from);
    }
    private static void invalid(Runnable action) { try { action.run(); throw new AssertionError("accepted invalid schedule"); } catch(IllegalArgumentException expected) { checks++; } }
    public static void main(String[] args) {
        DailySchedule work=plan(540,720,840,1080);
        check(!work.active(at("2026-10-22T08:59:59"),SH),"before start");
        check(work.active(at("2026-10-22T09:00:00"),SH),"inclusive start");
        check(!work.active(at("2026-10-22T12:00:00"),SH),"exclusive end");
        change(work,"2026-10-22T09:00:00","2026-10-22T12:00:00");
        change(work,"2026-10-22T12:00:00","2026-10-22T14:00:00");
        change(work,"2026-10-22T18:00:00","2026-10-23T09:00:00");
        // Separated alarm-like ranges must leave the gap usable, every day.
        for (int minute=720;minute<840;minute++)
            check(!work.active(at("2026-10-22T00:00:00").plusSeconds(minute*60L),SH),"separated noon gap remains free");
        DailySchedule migrated=DailySchedule.decode("540,720;840,1080;1320,420");
        check(migrated.windows.size()==3 && migrated.hasEnabledWindows(),"old plan retained");
        for(DailySchedule.Window w:migrated.windows) check(w.enabled,"legacy rows remain enabled");
        check(migrated.encode().equals("540,720,1;840,1080,1;1320,420,1"),"migrate two columns to per-row state");
        DailySchedule switched=DailySchedule.decode("540,720,1;840,1080,0;1320,420,1");
        check(!switched.active(at("2026-10-22T15:00:00"),SH),"disabled range does not block");
        check(switched.active(at("2026-10-22T09:01:00"),SH),"other ranges remain active");
        change(switched,"2026-10-22T12:00:00","2026-10-22T22:00:00");
        check(!DailySchedule.decode(switched.encode()).windows.get(1).enabled,"disabled state survives save/reload");
        DailySchedule allOff=DailySchedule.decode("540,720,0;1320,420,0");
        check(!allOff.hasEnabledWindows(),"all switches off");
        check(allOff.nextChange(at("2026-10-22T08:00:00"),SH)==null,"all off has no next alarm");
        check(!allOff.active(at("2026-10-22T23:00:00"),SH),"disabled overnight does not block");
        check(DailySchedule.decode("540,720;840,1080,0").windows.get(0).enabled,"mixed legacy and new rows supported");
        DailySchedule sleep=plan(1320,420);
        for(int day=1;day<=28;day++) {
            String date=String.format("2026-11-%02d",day);
            check(sleep.active(at(date+"T00:00:00"),SH),"daily recurrence midnight");
            check(sleep.active(at(date+"T22:00:00"),SH),"daily recurrence start");
            check(!sleep.active(at(date+"T07:00:00"),SH),"daily recurrence end");
        }
        change(sleep,"2026-10-22T23:59:00","2026-10-23T07:00:00");
        DailySchedule overlap=plan(540,720,660,780,780,840);
        change(overlap,"2026-10-22T09:00:00","2026-10-22T14:00:00");
        DailySchedule full=plan(0,720,720,0);
        check(full.nextChange(at("2026-10-22T00:00:00"),SH)==null,"full union has no false release");
        check(new DailySchedule(Collections.emptyList()).nextChange(Instant.now(),SH)==null,"empty");
        check(sleep.windows.get(0).label().equals("22:00 — 次日 07:00"),"overnight label");
        invalid(()->new DailySchedule.Window(5,5)); invalid(()->new DailySchedule.Window(-1,2)); invalid(()->new DailySchedule.Window(0,1440));
        for(String bad:Arrays.asList("a,b","1","1,2;","1,1","1,2,3","-1,2","1,2,true","1,2,","1,2,0,1")) invalid(()->DailySchedule.decode(bad));
        invalid(()->DailySchedule.decode("1,2;1,2;1,2;1,2;1,2;1,2;1,2;1,2;1,2"));
        Random random=new Random(137);
        Instant midnight=at("2026-10-22T00:00:00");
        for(int sample=0;sample<120;sample++) {
            java.util.ArrayList<DailySchedule.Window> windows=new java.util.ArrayList<>();
            for(int j=0;j<1+random.nextInt(8);j++) { int a=random.nextInt(1440),b=random.nextInt(1439);if(b>=a)b++;windows.add(new DailySchedule.Window(a,b,random.nextBoolean())); }
            DailySchedule s=new DailySchedule(windows),decoded=DailySchedule.decode(s.encode());
            check(s.encode().equals(decoded.encode()),"serialization roundtrip");
            // Independent oracle: mark each minute by walking enabled durations across midnight.
            boolean[] expectedMinutes=new boolean[1440];
            for(DailySchedule.Window w:windows) if(w.enabled)
                for(int offset=0;offset<(w.end-w.start+1440)%1440;offset++) expectedMinutes[(w.start+offset)%1440]=true;
            for(int minute=0;minute<1440;minute++) {
                check(s.active(midnight.plusSeconds(minute*60L),SH)==expectedMinutes[minute],"enabled-minute union");
            }
            Instant now=midnight.plusSeconds(random.nextInt(86400)); boolean initial=s.active(now,SH); Instant expected=null;
            long nextMinute=now.getEpochSecond()/60*60+60;
            for(int offset=0;offset<=1440;offset++) { Instant candidate=Instant.ofEpochSecond(nextMinute+offset*60L); if(s.active(candidate,SH)!=initial){expected=candidate;break;} }
            check(java.util.Objects.equals(s.nextChange(now,SH),expected),"next-change independent brute-force oracle");
        }
        ZoneId ny=ZoneId.of("America/New_York");
        DailySchedule gap=plan(150,240);
        check(gap.nextChange(Instant.parse("2026-03-08T06:59:00Z"),ny).equals(Instant.parse("2026-03-08T07:00:00Z")),"DST missing start moves to jump");
        DailySchedule repeated=plan(90,105);
        check(repeated.nextChange(Instant.parse("2026-11-01T05:50:00Z"),ny).equals(Instant.parse("2026-11-01T06:30:00Z")),"DST repeated hour second start");
        DailySchedule jumpRelease=plan(60,90);
        check(jumpRelease.nextChange(Instant.parse("2026-11-01T05:59:00Z"),ny).equals(Instant.parse("2026-11-01T06:00:00Z")),"DST clock rollback reenters range");
        System.out.println("DailyScheduleTest: "+checks+" checks passed");
    }
}
