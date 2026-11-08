package com.kpgc.timerroot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether a TimerRoot log shows a real root shell.
 * "root shell started" is printed even when calibration fails, so that
 * line alone must not start the KernelSU stage.
 */
final class RootLog {

    private RootLog() {
    }

    static boolean shellConfirmed(String log) {
        if (log == null || log.contains("base calibration failed")) {
            return false;
        }
        return log.contains("uid=0(root)")
                || (log.contains("setuid(0)=0") && log.contains("resuid=0/0/"));
    }

    /** True when the exploit log says a child was left unreaped or parked. */
    static boolean parentMustStay(String log) {
        if (log == null) {
            return false;
        }
        return log.contains("holding zombie") || log.contains("parking without reap");
    }

    enum Outcome {
        RUNNING,
        FAILED,
        SUCCESS,
        INDETERMINATE
    }

    /**
     * Classifies one run. Silence is not a safe exit: without a confirmed
     * shell, a dead process and no explicit failure stays INDETERMINATE.
     * A live process stays RUNNING even when the UI has stopped reading.
     */
    static Outcome classify(String runLog, boolean alive, boolean zombies) {
        if (shellConfirmed(runLog)) {
            return Outcome.SUCCESS;
        }
        if (alive) {
            return Outcome.RUNNING;
        }
        if (zombies || parentMustStay(runLog)) {
            return Outcome.INDETERMINATE;
        }
        if (runLog != null && (runLog.contains("base calibration failed")
                || runLog.contains("setuid(0) failed")
                || runLog.contains("openRoot failed"))) {
            return Outcome.FAILED;
        }
        return Outcome.INDETERMINATE;
    }

    static boolean stopBlocked(String runLog, boolean alive, boolean zombies) {
        return alive || zombies || parentMustStay(runLog)
                || classify(runLog, alive, zombies) != Outcome.FAILED;
    }

    static boolean aliveOf(String observe) {
        return observe != null
                && (observe.contains("\nalive=1") || observe.startsWith("alive=1")
                || observe.contains("\ndetachedAlive=1")
                || observe.startsWith("detachedAlive=1"));
    }

    static boolean zombiesOf(String observe) {
        return fieldPresent(observe, "zombies") || fieldPresent(observe, "detachedZombies");
    }

    static String outputOf(String observe) {
        if (observe == null) {
            return "";
        }
        StringBuilder lines = new StringBuilder();
        for (String line : observe.split("\n")) {
            if (line.startsWith("out=")) {
                lines.append(line.substring(4)).append('\n');
            }
        }
        return lines.toString();
    }

    private static boolean fieldPresent(String text, String key) {
        if (text == null) {
            return false;
        }
        Matcher matcher = Pattern.compile("(?m)^" + key + "=(.*)$").matcher(text);
        return matcher.find() && !matcher.group(1).trim().isEmpty();
    }
}
