package com.phonefocus.guard;

import java.util.Random;

public final class ScreenSessionTest {
    private static int checks;
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        ScreenSession s = new ScreenSession();
        check(!s.poll(999_999), "no reminders while off");
        check(s.elapsed(999_999) == 0, "off time not accumulated");
        s.screenOn(0);
        check(!s.poll(179_999), "no alert before three minutes");
        check(s.poll(180_000), "first alert at three minutes");
        check(!s.poll(180_000), "only one alert per threshold");
        check(!s.poll(359_999), "wait for next interval");
        check(s.poll(360_000), "repeat at six minutes");
        s.screenOff();
        check(!s.poll(999_999), "off cancels repeats");
        s.screenOn(1_000_000);
        check(!s.poll(1_179_999), "new session full grace period");
        check(s.poll(1_180_000), "new session fires correctly");
        s.screenOff(); s.screenOn(10_000); s.screenOn(170_000);
        check(s.poll(190_000), "duplicate screen-on does not reset timer");
        s.screenOff(); s.screenOn(0);
        check(s.poll(1_000_000), "late callback emits one reminder");
        check(!s.poll(1_000_000), "late callback does not burst");
        check(s.remaining(1_000_000) == 180_000, "late delivery starts new interval");
        s.screenOff(); s.screenOn(8_000_000_000L);
        check(s.poll(8_000_180_000L), "long uptime has no int overflow");
        s.screenOff(); s.screenOn(0); s.screenOff(); s.screenOn(179_999);
        check(!s.poll(180_000), "one millisecond screen-off resets continuous usage");
        // Compare event sequences against an independent elapsed-since-delivery oracle.
        Random random = new Random(20261021);
        boolean active = false;
        long now = 0, since = 0, delivered = -1;
        s.screenOff();
        for (int i = 0; i < 50_000; i++) {
            now += random.nextInt(90_001);
            int event = random.nextInt(5);
            if (event == 0) {
                s.screenOff(); active = false; delivered = -1;
            } else if (event == 1) {
                s.screenOn(now);
                if (!active) { active = true; since = now; delivered = -1; }
            } else {
                long anchor = delivered < 0 ? since : delivered;
                boolean expected = active && now - anchor >= 180_000;
                check(s.poll(now) == expected, "randomized delivery " + i);
                if (expected) delivered = now;
            }
            check(s.elapsed(now) == (active ? now - since : 0), "randomized continuous duration " + i);
            check(s.isActive() == active, "randomized state " + i);
        }
        System.out.println("PASS: " + checks + " checks; boundary, repeat, screen-off, duplicate, delay, long uptime, randomized sequences.");
    }
}
