package com.phonefocus.guard;

/** The screen-off decision path used by the service; Android calls live behind Locker. */
public final class AutoScreenOff {
    public interface Locker {
        boolean isAuthorized();
        void lockNow();
    }
    public enum Result { NONE, LOCK_REQUESTED, PERMISSION_REQUIRED, FAILED }
    private final ScreenSession session = new ScreenSession();
    private final Locker locker;

    public AutoScreenOff(Locker locker) { this.locker = locker; }
    public void screenOn(long now) { session.screenOn(now); }
    public void screenOff() { session.screenOff(); }
    public boolean isActive() { return session.isActive(); }
    public long elapsed(long now) { return session.elapsed(now); }
    public long remaining(long now) { return session.remaining(now); }

    public Result poll(long now, boolean interactive) {
        if (!interactive) { screenOff(); return Result.NONE; }
        if (!session.isActive()) return Result.NONE;
        // Check authorization while counting too, so revocation stops monitoring promptly.
        try {
            if (!locker.isAuthorized()) { screenOff(); return Result.PERMISSION_REQUIRED; }
            if (!session.poll(now)) return Result.NONE;
            locker.lockNow();
            screenOff(); // No duplicate lock requests while waiting for the screen-off broadcast.
            return Result.LOCK_REQUESTED;
        } catch (SecurityException revokedDuringCall) {
            screenOff();
            return Result.PERMISSION_REQUIRED;
        } catch (RuntimeException failure) {
            screenOff();
            return Result.FAILED;
        }
    }
}
