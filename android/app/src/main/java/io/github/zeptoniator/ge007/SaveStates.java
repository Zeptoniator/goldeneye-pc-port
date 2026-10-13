package io.github.zeptoniator.ge007;

import android.app.Activity;
import android.os.SystemClock;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;

/**
 * Savestates (port/src/savestate.c through port/android/android_savestate.c):
 * the files live in data/states/ (US) or data/states-<region>/ next to the
 * Bond file saves.
 *
 * Slot 0 is the automatic one, written every 45 s of a mission and when the
 * app leaves the screen, and offered back at the next launch; slots 1-3 are
 * the player's.
 */
final class SaveStates {
    static final int AUTO = 0, SLOTS = 4;

    /**
     * A solo mission (one that could be saved) ran in this process. Only
     * then does leaving from the front end drop the automatic state: the
     * mission it held is over. Opening the app and leaving again keeps it.
     */
    static volatile boolean soloMissionSeen;

    static native void nativeSave(String path);
    static native void nativeSaveLong(String path);
    /** Periodic automatic save to path during missions (0 s = off). */
    static native void nativeSetAuto(String path, int periodS);
    static native void nativeLoad(String path);
    static native boolean nativeBusy();
    static native boolean nativeCanSave();
    static native int nativeLastResult();
    static native String nativeLastMessage();
    /** {status (1 loadable, 0 other build, -1 none), stage, difficulty, savedAt (s)} */
    static native long[] nativePeek(String path);

    private SaveStates() {
    }

    static File file(Activity a, int slot) {
        // One directory per region: a state loads only in the game that wrote it.
        String region = Regions.active(a);
        File dir = new File(SetupActivity.dataDir(a),
                Regions.NTSC.equals(region) ? "states" : "states-" + region);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, slot == AUTO ? "auto.ge7s" : ("slot" + slot + ".ge7s"));
    }

    /** Request a save and wait (at most timeoutMs) until it is on disk. */
    static boolean saveAndWait(Activity a, int slot, long timeoutMs) {
        if (slot == AUTO) {
            nativeSaveLong(file(a, slot).getPath());
        } else {
            nativeSave(file(a, slot).getPath());
        }
        long end = SystemClock.uptimeMillis() + timeoutMs;
        while (nativeBusy() && SystemClock.uptimeMillis() < end) {
            SystemClock.sleep(10);
        }
        return !nativeBusy() && nativeLastResult() == 1;
    }

    static String describe(Activity a, long[] info) {
        if (info == null || info[0] < 0) {
            return a.getString(R.string.ss_empty);
        }
        if (info[0] == 0) {
            return a.getString(R.string.ss_other_build);
        }
        String when = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(info[3] * 1000L));
        return stageName((int) info[1]) + " · " + difficultyName(a, (int) info[2]) + " · " + when;
    }

    static String difficultyName(Activity a, int d) {
        switch (d) {
            case 0: return "Agent";
            case 1: return "Secret Agent";
            case 2: return "00 Agent";
            default: return "007";
        }
    }

    /** Solo mission names by stage id (src/bondconstants.h LEVELID). */
    static String stageName(int stage) {
        switch (stage) {
            case 33: return "Dam";
            case 34: return "Facility";
            case 35: return "Runway";
            case 36: return "Surface 1";
            case 9: return "Bunker 1";
            case 20: return "Silo";
            case 26: return "Frigate";
            case 43: return "Surface 2";
            case 27: return "Bunker 2";
            case 22: return "Statue";
            case 24: return "Archives";
            case 29: return "Streets";
            case 30: return "Depot";
            case 25: return "Train";
            case 37: return "Jungle";
            case 23: return "Control";
            case 39: return "Caverns";
            case 41: return "Cradle";
            case 28: return "Aztec";
            case 32: return "Egyptian";
            default: return "Stage " + stage;
        }
    }
}
