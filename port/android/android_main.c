/*
 * android_main.c — the Android entry point (SDLActivity calls SDL_main).
 *
 * The port resolves everything relative to the working directory: "$S/"
 * is ./data/ (port/src/system.c sysResolvePath), where the ROM, the PC asset
 * sidecars, ge007.ini, the EEPROM save and ge007.log live. An app process
 * starts in "/", so move to the app's external files directory first:
 *
 *   /storage/emulated/0/Android/data/<package>/files/data/...
 *
 * which the user (or scripts/android/push-data.sh) can fill over USB.
 * Arguments come from the launching Intent (GoldenEyeActivity.getArguments).
 */
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include <android/log.h>
#include <SDL.h>

int portMain(int argc, char **argv);

int SDL_main(int argc, char *argv[])
{
    const char *base = SDL_AndroidGetExternalStoragePath();
    if (!base) {
        /* No shared storage (rare): fall back to the private files dir. */
        base = SDL_AndroidGetInternalStoragePath();
    }
    if (!base || chdir(base) != 0) {
        __android_log_print(ANDROID_LOG_FATAL, "ge007", "cannot enter the data directory %s: %s",
                            base ? base : "(none)", strerror(errno));
        return 1;
    }
    /* Created by the app itself, so it owns it (a directory made by adb in
     * Android/data is not writable by the app). */
    mkdir("data", 0775);
    __android_log_print(ANDROID_LOG_INFO, "ge007", "data directory: %s/data", base);

    /* The game is not re-entrant (static state everywhere, and the 4 GiB
     * address window stays reserved), but Android keeps the process alive
     * after SDL_main returns and would run a relaunch in it. Leave for good. */
    int rc = portMain(argc, argv);
    __android_log_print(ANDROID_LOG_INFO, "ge007", "game exited (%d)", rc);
    exit(rc);
}
