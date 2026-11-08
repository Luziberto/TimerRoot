package com.kpgc.timerroot;

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
}
