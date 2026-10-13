#ifndef PORT_ANDROID_TOUCH_H
#define PORT_ANDROID_TOUCH_H

/* On-screen controls (port/android/android_touch.c). Android builds only. */

#include <SDL.h>

/* Attach the virtual pad (input thread, after SDL_INIT_GAMECONTROLLER). */
void androidTouchAttach(void);
void androidTouchDetach(void);
/* Once per input poll: attach/detach to follow the overlay's state. */
void androidTouchUpdate(void);
/* Drain the look-drag deltas accumulated since the last call (pixels). */
void androidTouchTakeLook(double *dx, double *dy);
/* True when this joystick instance is the on-screen pad (input.c seats it). */
int androidTouchIsPad(SDL_JoystickID inst);
/* F10 overlay: the finger as a pointer (window pixels); returns "pressed". */
int androidTouchPointer(int *x, int *y);

#endif
