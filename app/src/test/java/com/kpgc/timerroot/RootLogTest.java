package com.kpgc.timerroot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
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

    @Test
    public void heldZombieMeansParentMustStay() {
        assertTrue(RootLog.parentMustStay("[-] plant1 burst missed; holding zombie"));
        assertTrue(RootLog.parentMustStay(
                "[!] fake cred page held; reboot to clean -- parking without reap (zombie held)"));
        assertFalse(RootLog.parentMustStay("installed 307384 bytes"));
        assertFalse(RootLog.parentMustStay(null));
    }

    @Test
    public void liveProcessStaysRunningAfterTheUiWait() {
        assertEquals(RootLog.Outcome.RUNNING,
                RootLog.classify("root shell started\nzombie code=1 status=42", true, true));
    }

    @Test
    public void silenceIsNotASafeExit() {
        assertEquals(RootLog.Outcome.INDETERMINATE,
                RootLog.classify("root shell started", false, false));
    }

    @Test
    public void retainedZombieWithoutTheParkingLineIsIndeterminate() {
        assertEquals(RootLog.Outcome.INDETERMINATE,
                RootLog.classify("root shell started", false, true));
        assertTrue(RootLog.stopBlocked("root shell started", false, true));
    }

    @Test
    public void calibrationFailureIsFailedAndStopCanClose() {
        String log = "[-] base calibration failed\n";
        assertEquals(RootLog.Outcome.FAILED, RootLog.classify(log, false, false));
        assertFalse(RootLog.stopBlocked(log, false, false));
    }

    @Test
    public void successDoesNotAuthorizeStop() {
        String log = "setuid(0)=0\nresuid=0/0/2000\n";
        assertEquals(RootLog.Outcome.SUCCESS, RootLog.classify(log, true, false));
        assertTrue(RootLog.stopBlocked(log, true, false));
    }

    @Test
    public void oldKsuMarkerIsNotAlive() {
        String observe = "pid=21561\nalive=1\nzombies=21632\n"
                + "detached=-1\ndetachedAlive=0\ndetachedZombies=\n"
                + "out=still running\n";
        assertTrue(RootLog.aliveOf(observe));
        assertTrue(RootLog.zombiesOf(observe));
        assertEquals("still running\n", RootLog.outputOf(observe));
    }
}
