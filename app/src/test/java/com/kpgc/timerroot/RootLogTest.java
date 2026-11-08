package com.kpgc.timerroot;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RootLogTest {

    @Test
    public void calibrationFailureIsNotRoot() {
        String log = "root shell started\n"
                + "[base] made=512 best=0 count=0\n"
                + "[-] base calibration failed\n";
        assertFalse(RootLog.shellConfirmed(log));
    }

    @Test
    public void startedLineAloneIsNotRoot() {
        assertFalse(RootLog.shellConfirmed("root shell started"));
    }

    @Test
    public void confirmedShellReportIsRoot() {
        String log = "[+] child report uid=0 euid=2000 setuid(0)=0 "
                + "errno=0 resuid=0/0/2000\n"
                + "sh: can't find tty fd: No such device or address\n";
        assertTrue(RootLog.shellConfirmed(log));
    }

    @Test
    public void idOutputIsRoot() {
        assertTrue(RootLog.shellConfirmed("uid=0(root) gid=0(root)"));
    }

    @Test
    public void nullLogIsNotRoot() {
        assertFalse(RootLog.shellConfirmed(null));
    }
}
