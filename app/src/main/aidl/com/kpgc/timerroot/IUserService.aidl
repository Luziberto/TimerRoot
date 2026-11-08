package com.kpgc.timerroot;

interface IUserService {
    void destroy() = 16777114;
    void exit() = 1;
    String install(String apkPath) = 2;
    String run(String args) = 3;
    String runDetached(String args) = 4;
    String readLog() = 5;
    String openRoot() = 6;
    String execRoot(String cmd) = 7;
    String closeRoot() = 8;
    String info() = 9;
    String rebootDevice() = 10;
    String installFromBytes(in byte[] payload) = 11;
    /** seq 0 starts dest+".new"; last chunk chmod 755 and renames onto dest. */
    String installFileChunk(String dest, int seq, in byte[] chunk, boolean last) = 12;
    /** pid, alive, unreaped child zombies, and stdout lines since the last call. */
    String observe() = 13;
}
