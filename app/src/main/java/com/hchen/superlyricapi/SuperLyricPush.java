package com.hchen.superlyricapi;

/**
 * Stable hook surface used by SuperLyricApi 2.4. These bodies must stay empty:
 * the system module replaces/intercepts them in the player process.
 */
public final class SuperLyricPush {
    private SuperLyricPush() {}

    public static void onStop(SuperLyricData data) {}

    public static void onSuperLyric(SuperLyricData data) {}
}
