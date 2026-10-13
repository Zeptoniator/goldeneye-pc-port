/*
 * android_touch.c — on-screen controls for the Android build.
 *
 * The Java overlay (android/.../TouchOverlay.java) draws the controls and
 * forwards them here over JNI:
 *
 *   - sticks and buttons drive an SDL *virtual* game controller, so the
 *     port's whole Xbox-pad layout (port/src/input.c: A use, X reload,
 *     Y next weapon, B gadgets, triggers fire/aim, Start, Back = options,
 *     stick click = crouch, menus) applies unchanged;
 *   - a drag on the right of the screen is "look": its deltas are handed to
 *     input.c as grabbed-mouse motion (androidTouchTakeLook), i.e. the PC
 *     mouse-look path (direct look in hipfire, GEPD-style aim while aiming).
 *
 * The JNI entry points run on the Android UI thread; SDL's virtual-joystick
 * setters lock the joystick list, and the look accumulator has its own lock.
 */
#include <jni.h>
#include <SDL.h>

#include <android/log.h>

#include "android_touch.h"
#include "system.h"   /* sysArgGetString */

#include <stdlib.h>

int inputTouchContext(void); /* port/src/input.c */
int inputTouchHasGadget(void); /* port/src/input.c */

#define TOUCH_NBUTTONS (SDL_CONTROLLER_BUTTON_DPAD_RIGHT + 1)

static SDL_Joystick *s_joy;
static SDL_atomic_t s_wanted = { 1 };   /* Java: hidden while a real pad is connected */
static SDL_SpinLock s_lookLock;
static double s_lookDX, s_lookDY;

/* F10 options overlay: the finger as an absolute pointer in window pixels
 * (optionsoverlay.c reads it in place of SDL_GetMouseState). A press stays
 * visible for at least one read, so a tap shorter than a poll still clicks. */
static SDL_SpinLock s_ptrLock;
static int s_ptrX, s_ptrY, s_ptrDown, s_ptrPressUnread;

void optionsOverlayScroll(int dir);   /* port/src/optionsoverlay.c */
static void attachTestPads(void);

static int SDLCALL touchRumble(void *userdata, Uint16 lo, Uint16 hi)
{
    (void)userdata;
    /* N64 Rumble Pak -> the phone's vibrator, through the activity. */
    JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
    if (!env) {
        return -1;
    }
    jclass cls = (*env)->FindClass(env, "io/github/zeptoniator/ge007/GoldenEyeActivity");
    if (!cls) {
        (*env)->ExceptionClear(env);
        return -1;
    }
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, "rumble", "(I)V");
    if (mid) {
        (*env)->CallStaticVoidMethod(env, cls, mid, (jint)(lo > hi ? lo : hi));
    }
    (*env)->ExceptionClear(env);
    (*env)->DeleteLocalRef(env, cls);
    return 0;
}

void androidTouchAttach(void)
{
    if (s_joy || !SDL_AtomicGet(&s_wanted)) {
        return;
    }
    SDL_VirtualJoystickDesc d;
    SDL_zero(d);
    d.version = SDL_VIRTUAL_JOYSTICK_DESC_VERSION;
    d.type = SDL_JOYSTICK_TYPE_GAMECONTROLLER;
    d.naxes = SDL_CONTROLLER_AXIS_MAX;
    d.nbuttons = TOUCH_NBUTTONS;
    d.button_mask = (1u << TOUCH_NBUTTONS) - 1u;
    d.axis_mask = (1u << SDL_CONTROLLER_AXIS_MAX) - 1u;
    d.name = "Touch controls";
    d.Rumble = touchRumble;
    int index = SDL_JoystickAttachVirtualEx(&d);
    if (index < 0) {
        __android_log_print(ANDROID_LOG_WARN, "ge007", "touch: cannot attach the virtual pad: %s",
                            SDL_GetError());
        return;
    }
    s_joy = SDL_JoystickOpen(index);
    __android_log_print(ANDROID_LOG_INFO, "ge007", "touch: virtual pad attached (device %d)", index);
    attachTestPads();
}

/* --test-pads N (testing split-screen without hardware): N idle virtual
 * controllers, seated after the on-screen pad like real ones. */
static void attachTestPads(void)
{
    static int done;
    const char *n = sysArgGetString("--test-pads");
    if (done || !n) {
        return;
    }
    done = 1;
    for (int i = 0; i < atoi(n) && i < 3; i++) {
        SDL_VirtualJoystickDesc d;
        SDL_zero(d);
        d.version = SDL_VIRTUAL_JOYSTICK_DESC_VERSION;
        d.type = SDL_JOYSTICK_TYPE_GAMECONTROLLER;
        d.naxes = SDL_CONTROLLER_AXIS_MAX;
        d.nbuttons = TOUCH_NBUTTONS;
        d.button_mask = (1u << TOUCH_NBUTTONS) - 1u;
        d.axis_mask = (1u << SDL_CONTROLLER_AXIS_MAX) - 1u;
        d.name = "Test pad";
        if (SDL_JoystickAttachVirtualEx(&d) >= 0) {
            __android_log_print(ANDROID_LOG_INFO, "ge007", "touch: test pad %d attached", i + 1);
        }
    }
}

int androidTouchIsPad(SDL_JoystickID inst)
{
    return s_joy && inst >= 0 && SDL_JoystickInstanceID(s_joy) == inst;
}

void androidTouchDetach(void)
{
    if (!s_joy) {
        return;
    }
    SDL_JoystickID id = SDL_JoystickInstanceID(s_joy);
    SDL_JoystickClose(s_joy);
    s_joy = NULL;
    for (int i = 0; i < SDL_NumJoysticks(); i++) {
        if (SDL_JoystickGetDeviceInstanceID(i) == id) {
            SDL_JoystickDetachVirtual(i);
            break;
        }
    }
    __android_log_print(ANDROID_LOG_INFO, "ge007", "touch: virtual pad detached");
}

void androidTouchUpdate(void)
{
    /* Poll thread: follow the overlay's on/off state (real pad plugged in or
     * out). Attaching/detaching posts the device events input.c's hot-plug
     * code (D450) already handles. */
    int wanted = SDL_AtomicGet(&s_wanted);
    if (wanted && !s_joy) {
        androidTouchAttach();
    } else if (!wanted && s_joy) {
        androidTouchDetach();
    }
}

void androidTouchTakeLook(double *dx, double *dy)
{
    SDL_AtomicLock(&s_lookLock);
    *dx = s_lookDX;
    *dy = s_lookDY;
    s_lookDX = s_lookDY = 0.0;
    SDL_AtomicUnlock(&s_lookLock);
}

JNIEXPORT void JNICALL
Java_io_github_zeptoniator_ge007_TouchOverlay_nativeAxis(JNIEnv *env, jclass cls, jint axis, jint value)
{
    (void)env; (void)cls;
    if (s_joy && axis >= 0 && axis < SDL_CONTROLLER_AXIS_MAX) {
        SDL_JoystickSetVirtualAxis(s_joy, axis, (Sint16)SDL_clamp(value, -32768, 32767));
    }
}

JNIEXPORT void JNICALL
Java_io_github_zeptoniator_ge007_TouchOverlay_nativeButton(JNIEnv *env, jclass cls, jint button, jboolean down)
{
    (void)env; (void)cls;
    if (s_joy && button >= 0 && button < TOUCH_NBUTTONS) {
        SDL_JoystickSetVirtualButton(s_joy, button, down ? SDL_PRESSED : SDL_RELEASED);
    }
}

JNIEXPORT void JNICALL
Java_io_github_zeptoniator_ge007_TouchOverlay_nativeLook(JNIEnv *env, jclass cls, jfloat dx, jfloat dy)
{
    (void)env; (void)cls;
    SDL_AtomicLock(&s_lookLock);
    s_lookDX += dx;
    s_lookDY += dy;
    SDL_AtomicUnlock(&s_lookLock);
}

int androidTouchPointer(int *x, int *y)
{
    SDL_AtomicLock(&s_ptrLock);
    *x = s_ptrX;
    *y = s_ptrY;
    int down = s_ptrDown || s_ptrPressUnread;
    s_ptrPressUnread = 0;
    SDL_AtomicUnlock(&s_ptrLock);
    return down;
}

/* action: 0 move, 1 press, 2 release. */
JNIEXPORT void JNICALL
Java_io_github_zeptoniator_ge007_TouchOverlay_nativePointer(JNIEnv *env, jclass cls, jint x, jint y, jint action)
{
    (void)env; (void)cls;
    SDL_AtomicLock(&s_ptrLock);
    s_ptrX = x;
    s_ptrY = y;
    if (action == 1) {
        s_ptrDown = 1;
        s_ptrPressUnread = 1;
    } else if (action == 2) {
        s_ptrDown = 0;
    }
    SDL_AtomicUnlock(&s_ptrLock);
}

/* A vertical swipe on the panel: list steps (wheel notches, < 0 = up). */
JNIEXPORT void JNICALL
Java_io_github_zeptoniator_ge007_TouchOverlay_nativeOptionsScroll(JNIEnv *env, jclass cls, jint steps)
{
    (void)env; (void)cls;
    optionsOverlayScroll(steps);
}

JNIEXPORT void JNICALL
Java_io_github_zeptoniator_ge007_TouchOverlay_nativeSetEnabled(JNIEnv *env, jclass cls, jboolean on)
{
    (void)env; (void)cls;
    SDL_AtomicSet(&s_wanted, on ? 1 : 0);
}

/* Bond carries a gadget B can select (the B tile is disabled otherwise). */
JNIEXPORT jboolean JNICALL
Java_io_github_zeptoniator_ge007_TouchOverlay_nativeHasGadget(JNIEnv *env, jclass cls)
{
    (void)env; (void)cls;
    return inputTouchHasGadget() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_io_github_zeptoniator_ge007_TouchOverlay_nativeContext(JNIEnv *env, jclass cls)
{
    (void)env; (void)cls;
    return s_joy ? inputTouchContext() : 0;
}
