package io.github.zeptoniator.ge007;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The ROM regions this APK carries (app/build.gradle ge.regions): one game
 * library per region -- libmain.so (US), libmain_pal.so, libmain_jpn.so --
 * and one data set per region in data/ (ge007.&lt;region&gt;.z64 and its
 * converted sidecars). The active region is the last ROM imported or picked
 * in SetupActivity.
 */
final class Regions {
    static final String NTSC = "ntsc-final", PAL = "pal-final", JPN = "jpn-final";
    static final String[] ALL = { NTSC, PAL, JPN };
    /** SHA-1 of the supported dumps (README, "What's a ROM?"), in ALL's order. */
    private static final String[] SHA1 = {
        "abe01e4aeb033b6c0836819f549c791b26cfde83",
        "167c3c433dec1f1eb921736f7d53fac8cb45ee31",
        "2a5dade32f7fad6c73c659d2026994632c1b3174",
    };
    private static final String PREFS = "setup", KEY = "region";

    private Regions() {
    }

    /** True when this APK has the game library for the region. */
    static boolean built(String region) {
        for (String r : BuildConfig.GE_REGIONS.split(",")) {
            if (r.equals(region)) {
                return true;
            }
        }
        return false;
    }

    /** The region of a dump by its SHA-1 (of the .z64 byte order), or null. */
    static String forSha1(String sha1) {
        for (int i = 0; i < ALL.length; i++) {
            if (SHA1[i].equals(sha1)) {
                return ALL[i];
            }
        }
        return null;
    }

    static String sha1(String region) {
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].equals(region)) {
                return SHA1[i];
            }
        }
        return "";
    }

    /** System.loadLibrary name of the region's game library. */
    static String library(String region) {
        return NTSC.equals(region) ? "main" : "main_" + region.replace("-final", "");
    }

    static int label(String region) {
        return PAL.equals(region) ? R.string.region_pal
                : JPN.equals(region) ? R.string.region_jpn : R.string.region_ntsc;
    }

    static String active(Context c) {
        String r = prefs(c).getString(KEY, NTSC);
        return built(r) ? r : NTSC;
    }

    static void setActive(Context c, String region) {
        prefs(c).edit().putString(KEY, region).commit();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
