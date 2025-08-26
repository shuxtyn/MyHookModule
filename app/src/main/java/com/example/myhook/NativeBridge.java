
package com.example.myhook;

public class NativeBridge {
    static {
        try { System.loadLibrary("myhook_native"); } catch (Throwable ignored) {}
    }
    public static native void initHooks(String targetPkg);
    public static native void setOverrides(String[] keys, String[] values);
}
