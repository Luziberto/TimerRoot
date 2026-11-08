package com.kpgc.timerroot;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Runs inside the process that Shizuku starts as the shell user (uid 2000,
 * u:r:shell:s0), which is what the exploit needs for tracefs.
 */
public class UserService extends IUserService.Stub {

    private static final String BIN = "/data/local/tmp/timer";
    /**
     * Fallback looked up by the exploit script in
     * CVE-2026-98260 timer/timer.c write_ksu_script(), just before
     * "[ksu] ksud missing".
     */
    private static final String KSUD = "/data/local/tmp/ksud";
    private static final String LOG = "/data/local/tmp/timer.log";
    private static final int MAX_OUTPUT = 20000;

    private FileOutputStream chunkOut;
    private String chunkDest;
    private long chunkSize;

    private Process rootProcess;
    private BufferedWriter rootInput;
    private final BlockingQueue<String> rootLines = new LinkedBlockingQueue<>();

    public UserService() {
    }

    public UserService(android.content.Context context) {
    }

    @Override
    public void destroy() {
        closeRootInternal();
        System.exit(0);
    }

    @Override
    public void exit() {
        destroy();
    }

    @Override
    public String install(String apk) {
        try {
            try (ZipFile zf = new ZipFile(apk)) {
                ZipEntry entry = zf.getEntry("assets/timer");
                if (entry == null) {
                    return "assets/timer not found in " + apk
                            + " - use Prepare (reads assets via the app)";
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (InputStream in = zf.getInputStream(entry)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        bos.write(buf, 0, n);
                    }
                }
                return installFromBytes(bos.toByteArray());
            }
        } catch (Throwable t) {
            return "install failed: " + t;
        }
    }

    @Override
    public String installFromBytes(byte[] payload) {
        try {
            return installPayload(BIN, payload);
        } catch (Throwable t) {
            return "install failed: " + t;
        }
    }

    @Override
    public String installFileChunk(String dest, int seq, byte[] chunk, boolean last) {
        if (!BIN.equals(dest) && !KSUD.equals(dest)) {
            return "install failed: unsupported path " + dest;
        }
        if (chunk == null) {
            chunk = new byte[0];
        }
        synchronized (this) {
            try {
                if (seq == 0) {
                    closeChunk();
                    chunkDest = dest;
                    chunkSize = 0;
                    chunkOut = new FileOutputStream(dest + ".new");
                } else if (chunkOut == null || !dest.equals(chunkDest)) {
                    return "install failed: chunk out of order for " + dest;
                }
                if (chunk.length > 0) {
                    chunkOut.write(chunk);
                    chunkSize += chunk.length;
                }
                if (!last) {
                    return "chunk " + chunkSize;
                }
                long expected = chunkSize;
                chunkOut.getFD().sync();
                closeChunk();
                return finishInstall(dest, expected);
            } catch (Throwable t) {
                closeChunk();
                return "install failed: " + t;
            }
        }
    }

    private void closeChunk() {
        if (chunkOut != null) {
            try {
                chunkOut.close();
            } catch (IOException ignored) {
            }
            chunkOut = null;
        }
    }

    private String installPayload(String dest, byte[] payload)
            throws IOException, InterruptedException {
        if (payload == null || payload.length == 0) {
            return "install failed: empty payload for " + dest;
        }
        String tmp = dest + ".new";
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(payload);
            out.getFD().sync();
        }
        return finishInstall(dest, payload.length);
    }

    private String finishInstall(String dest, long expected)
            throws IOException, InterruptedException {
        String tmp = dest + ".new";
        // The old binary may still be mapped by a parked process; rename
        // over it instead of writing in place (ETXTBSY).
        Process move = Runtime.getRuntime().exec(new String[]{"/system/bin/sh",
                "-c", "chmod 755 " + tmp + " && mv -f " + tmp + " " + dest});
        int rc = move.waitFor();
        File bin = new File(dest);
        if (rc != 0 || !bin.exists() || bin.length() != expected) {
            return "install failed: chmod/mv rc=" + rc + " size=" + bin.length()
                    + " expected=" + expected + " dest=" + dest;
        }
        if (!bin.canExecute()) {
            return "install failed: " + dest + " is not executable";
        }
        return "installed " + bin.length() + " bytes at " + dest + " (uid="
                + android.system.Os.getuid() + ")";
    }

    private String requireBinary() {
        File bin = new File(BIN);
        if (!bin.exists() || bin.length() < 1024 || !bin.canExecute()) {
            return "timer missing at " + BIN + " - open the app and press Prepare first";
        }
        return null;
    }

    @Override
    public String run(String args) {
        String missing = requireBinary();
        if (missing != null) {
            return missing;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "/system/bin/sh", "-c", BIN + " " + args + " 2>&1");
            pb.directory(new File("/data/local/tmp"));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String text = readAll(p.getInputStream());
            if (!p.waitFor(90, TimeUnit.SECONDS)) {
                return text + "\n[timeout] still running after 90s - do not kill it,"
                        + " reboot to clean up";
            }
            return text;
        } catch (Throwable t) {
            return "run failed: " + t;
        }
    }

    @Override
    public String runDetached(String args) {
        String missing = requireBinary();
        if (missing != null) {
            return missing;
        }
        try {
            String cmd = "cd /data/local/tmp && setsid " + BIN + " " + args
                    + " >" + LOG + " 2>&1 </dev/null &";
            Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", cmd});
            Thread.sleep(2000);
            return readLog();
        } catch (Throwable t) {
            return "start failed: " + t;
        }
    }

    @Override
    public String readLog() {
        try (FileInputStream in = new FileInputStream(LOG)) {
            return readAll(in);
        } catch (Throwable t) {
            return "";
        }
    }

    @Override
    public String openRoot() {
        if (rootProcess != null && rootProcess.isAlive()) {
            return "root shell already open";
        }
        String missing = requireBinary();
        if (missing != null) {
            return missing;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(BIN, "shell");
            pb.directory(new File("/data/local/tmp"));
            pb.redirectErrorStream(true);
            rootProcess = pb.start();
            rootInput = new BufferedWriter(
                    new OutputStreamWriter(rootProcess.getOutputStream()));
            final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(rootProcess.getInputStream()));
            rootLines.clear();
            Thread pump = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        rootLines.offer(line);
                    }
                } catch (IOException ignored) {
                }
            });
            pump.setDaemon(true);
            pump.start();
            Thread.sleep(6000);
            String out = new String();
            long deadline = System.currentTimeMillis() + 20000;
            while (System.currentTimeMillis() < deadline) {
                String line = rootLines.poll(100, TimeUnit.MILLISECONDS);
                if (line == null) {
                    continue;
                }
                out += line + "\n";
                if (line.contains("uid=0(root)")) {
                    break;
                }
            }
            return "root shell started\n" + out.trim();
        } catch (Throwable t) {
            return "openRoot failed: " + t;
        }
    }

    @Override
    public String execRoot(String cmd) {
        if (rootProcess == null || !rootProcess.isAlive()) {
            return "root shell not open";
        }
        try {
            rootInput.write(cmd + " 2>&1\necho __END__\n");
            rootInput.flush();
            StringBuilder sb = new StringBuilder();
            long deadline = System.currentTimeMillis() + 30000;
            while (System.currentTimeMillis() < deadline) {
                String line = rootLines.poll(200, TimeUnit.MILLISECONDS);
                if (line == null) {
                    continue;
                }
                if (line.equals("__END__")) {
                    break;
                }
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Throwable t) {
            return "execRoot failed: " + t;
        }
    }

    @Override
    public String closeRoot() {
        closeRootInternal();
        return "root shell closed";
    }

    /** SELinux / Seccomp / ksud, as shown on the home card. */
    @Override
    public String info() {
        String selinux = execOut("getenforce");
        String seccomp = execOut("grep -m1 Seccomp /proc/self/status | awk '{print $2}'");
        String ksud = "not loaded";
        Matcher m = Pattern.compile("version=(ksud[^\\n]*)").matcher(readLog());
        if (m.find()) {
            ksud = m.group(1).trim();
        } else if (selinux.contains("Permissive")) {
            ksud = "missing";
        }
        String ksu = execOut("/system/bin/su -c id").contains("u:r:ksu:s0")
                ? "active" : "not loaded";
        return "selinux=" + selinux + "\nseccomp=" + seccomp + "\nksud=" + ksud
                + "\nksu=" + ksu + "\n";
    }

    @Override
    public String rebootDevice() {
        int rc = execCode("/system/bin/svc power reboot");
        if (rc != 0) {
            rc = execCode("/system/bin/su -c reboot");
        }
        if (rc != 0) {
            rc = execCode("/system/bin/reboot");
        }
        return rc == 0 ? "rebooting" : "reboot failed (rc=" + rc + ")";
    }

    private int execCode(String cmd) {
        try {
            Process p = new ProcessBuilder("/system/bin/sh", "-c", cmd)
                    .redirectErrorStream(true).start();
            return p.waitFor();
        } catch (Throwable t) {
            return -1;
        }
    }

    private String execOut(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String text = readAll(p.getInputStream()).trim();
            p.waitFor();
            return text.isEmpty() ? "unknown" : text;
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private void closeRootInternal() {
        try {
            if (rootInput != null) {
                rootInput.write("exit\n");
                rootInput.flush();
            }
        } catch (IOException ignored) {
        }
        if (rootProcess != null) {
            rootProcess.destroy();
            rootProcess = null;
        }
        rootInput = null;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        int total = 0;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
            total += n;
            if (total > MAX_OUTPUT) {
                break;
            }
        }
        return bos.toString("UTF-8");
    }
}
