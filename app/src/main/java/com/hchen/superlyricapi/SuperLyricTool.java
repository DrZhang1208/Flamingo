package com.hchen.superlyricapi;

/** Fields are intentionally public: the SuperLyric hook sets isEnabled at runtime. */
public final class SuperLyricTool {
    public static int apiVersion = BuildConfig.API_VERSION;
    public static boolean isEnabled = false;

    private SuperLyricTool() {}
}
