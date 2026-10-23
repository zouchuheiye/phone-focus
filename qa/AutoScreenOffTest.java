package com.phonefocus.guard;

public final class AutoScreenOffTest {
    private static int checks;
    private static final class FakeLocker implements AutoScreenOff.Locker {
        boolean allowed = true;
        boolean revokeDuringCall;
        boolean fail;
        int calls;
        @Override public boolean isAuthorized() { return allowed; }
        @Override public void lockNow() {
            calls++;
            if (revokeDuringCall) throw new SecurityException("revoked");
            if (fail) throw new IllegalStateException("OEM rejected request");
        }
    }
    private static void check(boolean result, String label) {
        checks++;
        if (!result) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        FakeLocker locker = new FakeLocker();
        AutoScreenOff guard = new AutoScreenOff(locker);
        check(guard.poll(999_999, true) == AutoScreenOff.Result.NONE, "never started");
        check(locker.calls == 0, "never locks without a session");
        guard.screenOn(0);
        for (long now = 0; now < 180_000; now += 1000) {
            check(guard.poll(now, true) == AutoScreenOff.Result.NONE, "early tick " + now);
            check(locker.calls == 0, "no premature system call " + now);
        }
        check(guard.poll(180_000, true) == AutoScreenOff.Result.LOCK_REQUESTED, "exact 180 second trigger");
        check(locker.calls == 1, "threshold invokes actual lock adapter exactly once");
        check(!guard.isActive(), "suspend after lock request until a new screen-on");
        check(guard.poll(180_001, true) == AutoScreenOff.Result.NONE, "no lock burst while awaiting broadcast");
        guard.screenOff();
        guard.screenOn(200_000);
        check(guard.poll(379_999, true) == AutoScreenOff.Result.NONE, "full grace period after wake");
        check(guard.poll(380_000, true) == AutoScreenOff.Result.LOCK_REQUESTED, "next bright session locks again");
        check(locker.calls == 2, "two bright sessions two calls");

        guard.screenOn(500_000);
        guard.screenOff();
        guard.screenOn(679_000);
        check(guard.poll(680_000, true) == AutoScreenOff.Result.NONE, "off at 179 seconds resets");
        guard.screenOn(850_000); // Duplicate broadcast must not postpone the deadline.
        check(guard.poll(859_000, true) == AutoScreenOff.Result.LOCK_REQUESTED, "duplicate screen-on keeps deadline");
        guard.screenOn(1_000_000);
        check(guard.poll(1_180_000, false) == AutoScreenOff.Result.NONE, "off-state check beats expired deadline");
        check(!guard.isActive(), "poll seeing off clears session even if broadcast missed");

        guard.screenOn(2_000_000);
        int before = locker.calls;
        locker.allowed = false;
        check(guard.poll(2_001_000, true) == AutoScreenOff.Result.PERMISSION_REQUIRED, "permission withdrawal before deadline");
        check(locker.calls == before, "no system lock after withdrawal");
        check(!guard.isActive(), "permission withdrawal ends session");
        locker.allowed = true; locker.revokeDuringCall = true;
        guard.screenOn(3_000_000);
        check(guard.poll(3_180_000, true) == AutoScreenOff.Result.PERMISSION_REQUIRED, "revocation race contained");
        check(guard.poll(3_180_001, true) == AutoScreenOff.Result.NONE, "no retry loop after revocation");
        locker.revokeDuringCall = false; locker.fail = true;
        guard.screenOn(4_000_000);
        check(guard.poll(4_180_000, true) == AutoScreenOff.Result.FAILED, "runtime failure exposed to service");
        check(!guard.isActive(), "failure cannot silently continue counting");
        locker.fail = false;
        guard.screenOn(5_000_000);
        check(guard.poll(9_000_000, true) == AutoScreenOff.Result.LOCK_REQUESTED, "delayed callback issues one lock");
        before = locker.calls;
        guard.poll(9_000_001, true);
        check(locker.calls == before, "delayed callback no catch-up lock burst");
        System.out.println("PASS: " + checks + " auto-screen-off checks; real service decision class with fake OS adapter, no device claim.");
    }
}
