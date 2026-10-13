/*
 * android_game.c — game options set from the Android UI (TouchSettingsPanel):
 * the game's own cheats (invincibility, infinite ammo, all guns), its
 * Auto-aim watch setting, and the port's Game.AllUnlocked.
 *
 * The Java UI thread only records requests; androidGameTick() applies them
 * on the game thread (from osRecvMesg on gfxFrameMsgQ, like
 * watchSettingsGameTick), the only thread allowed to call game setters and
 * the EEPROM path (see port/src/watchsettings.c).
 *
 * Cheats use the game's in-mission activation (the button-code path,
 * cheatButtonTurnOnCheatForPlayers), re-applied at every stage start while
 * the toggle is on. A toggle switched off only removes a cheat this file
 * turned on, never one chosen in the game's own Cheat menu.
 */
#include <jni.h>
#include <SDL.h>
#include <ultra64.h>
#include <bondconstants.h>
#include "player.h"
#include "front.h"
#include "cheat.h"
#include "system.h"
#include "config.h"
#include "watchsettings.h"

extern s32 g_StageNum;                       /* src/boss.c */
extern s32 portAllUnlocked;                  /* port/src/video.c */
extern void portAllUnlockedApply(void);      /* port/src/main.c */
extern void cheatButtonHandleCheatsTurnedOff(CHEAT_ID cheat_id); /* src/game/cheat.c */

/* Java's cheat indices -> game cheat ids. */
static const CHEAT_ID kCheats[] = { CHEAT_INVINCIBILITY, CHEAT_INFINITE_AMMO, CHEAT_ALLGUNS };
#define NCHEATS ((int)(sizeof(kCheats) / sizeof(kCheats[0])))

static SDL_atomic_t s_cheatWant;             /* bit i = kCheats[i] wanted */
static int s_cheatOurs;                      /* bit i = we turned kCheats[i] on (game thread) */
static SDL_atomic_t s_allUnlockedReq = { -1 };
static SDL_atomic_t s_autoAimReq = { -1 };

static int inStage(void)
{
    return g_StageNum != LEVELID_TITLE && g_CurrentPlayer != NULL &&
           (current_menu == MENU_RUN_STAGE || current_menu == MENU_INVALID) &&
           getPlayerCount() >= 1;
}

void androidGameTick(void)
{
    int v = SDL_AtomicSet(&s_allUnlockedReq, -1);
    if (v >= 0 && v != portAllUnlocked) {
        portAllUnlocked = v;
        portAllUnlockedApply();
        configSave();
    }

    v = SDL_AtomicSet(&s_autoAimReq, -1);
    if (v >= 0 && watchSettingsAvailable()) {
        watchSettingsSet(WATCH_SETTING_AUTOAIM, v, 1);
    }

    if (!inStage()) {
        s_cheatOurs = 0;   /* the game resets its cheat state per stage */
        return;
    }
    int want = SDL_AtomicGet(&s_cheatWant);
    for (int i = 0; i < NCHEATS; i++) {
        int on = cheatIsActive(kCheats[i]) ? 1 : 0;
        if ((want >> i) & 1) {
            if (!on) {
                cheatButtonTurnOnCheatForPlayers(kCheats[i]);
                s_cheatOurs |= 1 << i;
                sysLogPrintf(LOG_INFO, "android: cheat %d on", (int)kCheats[i]);
            }
        } else if (on && ((s_cheatOurs >> i) & 1)) {
            cheatButtonHandleCheatsTurnedOff(kCheats[i]);
            s_cheatOurs &= ~(1 << i);
            sysLogPrintf(LOG_INFO, "android: cheat %d off", (int)kCheats[i]);
        }
    }
}

JNIEXPORT void JNICALL
Java_io_github_zeptoniator_ge007_GameOptions_nativeSetCheat(JNIEnv *env, jclass cls, jint which, jboolean on)
{
    (void)env; (void)cls;
    if (which < 0 || which >= NCHEATS) {
        return;
    }
    for (;;) {
        int cur = SDL_AtomicGet(&s_cheatWant);
        int next = on ? (cur | (1 << which)) : (cur & ~(1 << which));
        if (SDL_AtomicCAS(&s_cheatWant, cur, next)) {
            break;
        }
    }
}

JNIEXPORT void JNICALL
Java_io_github_zeptoniator_ge007_GameOptions_nativeSetAllUnlocked(JNIEnv *env, jclass cls, jboolean on)
{
    (void)env; (void)cls;
    SDL_AtomicSet(&s_allUnlockedReq, on ? 1 : 0);
}

JNIEXPORT jboolean JNICALL
Java_io_github_zeptoniator_ge007_GameOptions_nativeGetAllUnlocked(JNIEnv *env, jclass cls)
{
    (void)env; (void)cls;
    int req = SDL_AtomicGet(&s_allUnlockedReq);
    return (req >= 0 ? req : portAllUnlocked) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_github_zeptoniator_ge007_GameOptions_nativeSetAutoAim(JNIEnv *env, jclass cls, jboolean on)
{
    (void)env; (void)cls;
    SDL_AtomicSet(&s_autoAimReq, on ? 1 : 0);
}

/* -1 when no Bond file is selected yet (front end before file select). */
JNIEXPORT jint JNICALL
Java_io_github_zeptoniator_ge007_GameOptions_nativeGetAutoAim(JNIEnv *env, jclass cls)
{
    (void)env; (void)cls;
    int req = SDL_AtomicGet(&s_autoAimReq);
    if (req >= 0) {
        return req;
    }
    return watchSettingsAvailable() ? (watchSettingsRead(WATCH_SETTING_AUTOAIM) ? 1 : 0) : -1;
}
