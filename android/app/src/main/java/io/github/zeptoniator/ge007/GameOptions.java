package io.github.zeptoniator.ge007;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Game options set from the touch settings panel: the game's own cheats,
 * its Auto-aim watch setting and the port's "All unlocked"
 * (port/android/android_game.c applies them on the game thread).
 *
 * Cheat toggles are ours (kept in the "touch" preferences and re-applied at
 * every launch and stage start); Auto-aim lives in the Bond file and
 * All-unlocked in ge007.ini, so those two are read back from the game.
 */
final class GameOptions {
    static final int CHEAT_INVINCIBLE = 0, CHEAT_INFINITE_AMMO = 1, CHEAT_ALL_GUNS = 2;
    static final String[] CHEAT_KEYS = { "cheat.invincible", "cheat.infiniteAmmo", "cheat.allGuns" };

    static native void nativeSetCheat(int which, boolean on);
    static native void nativeSetAllUnlocked(boolean on);
    static native boolean nativeGetAllUnlocked();
    static native void nativeSetAutoAim(boolean on);
    /** 1 on, 0 off, -1 when no Bond file is chosen yet. */
    static native int nativeGetAutoAim();

    private GameOptions() {
    }

    static boolean cheat(Context c, int which) {
        return prefs(c).getBoolean(CHEAT_KEYS[which], false);
    }

    static void setCheat(Context c, int which, boolean on) {
        prefs(c).edit().putBoolean(CHEAT_KEYS[which], on).apply();
        nativeSetCheat(which, on);
    }

    /** Push the saved cheat toggles to the game (at launch). */
    static void applySaved(Context c) {
        for (int i = 0; i < CHEAT_KEYS.length; i++) {
            nativeSetCheat(i, cheat(c, i));
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(TouchOverlay.PREFS, Context.MODE_PRIVATE);
    }
}
