package com.phonefocus.guard;

import java.util.ArrayList;
import java.util.List;

/** Always attempt every independent restore, and never report success after a partial failure. */
public final class PolicyRollback {
    public static final class Step {
        final String name;
        final Runnable action;
        public Step(String name, Runnable action) { this.name = name; this.action = action; }
    }
    public static List<String> run(List<Step> steps) {
        List<String> errors = new ArrayList<>();
        for (Step step : steps) {
            try { step.action.run(); }
            catch (RuntimeException failure) { errors.add(step.name + ": " + failure.getClass().getSimpleName()); }
        }
        return errors;
    }
    private PolicyRollback() {}
}
