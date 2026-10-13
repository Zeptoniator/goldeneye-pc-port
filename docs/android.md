# Android port (arm64, experimental)

The PC port runs on Android as `libmain.so` inside SDL2's Java activity,
with on-screen controls and a first-run ROM import. Same game code, same
port layer, same renderer (OpenGL ES 3.0 through fast3d's ES path).

**Verified (2026-10-13, Samsung SM-S948B, Android 17, Adreno 840):** boot, intro, the front end driven by touch (title, file, mission,
difficulty, briefing) into Dam with its intro cutscene, `-level_09`
(Bunker 1) played by touch (move, look, use a door, fire), the watch on
Back, background/resume, on-device asset conversion byte-identical to the
PC converters. 120 Hz presents with frame interpolation (99-120/s on Dam);
about 60 % of one core in game, 1 % in the background. **Not verified yet:** a whole campaign, split-screen
multiplayer, a real Bluetooth/USB controller (the overlay should hide
itself), 16 KB-page devices, devices with a 48-bit address space only.

## Building

Needs the Android SDK (platform 37), NDK 28.2.13676358, SDK CMake 3.31.6,
Gradle 9.x, Python 3 on the build host, network access on the first build
(Chaquopy fetches its Python runtime from Maven Central), and an SDL2 2.x
source tree:

```sh
scripts/android/fetch-sdl2.sh             # -> android/third_party/SDL2 (or pass -Pge.sdl2Dir=...)
cd android
gradle :app:assembleDebug                 # no wrapper is committed; any Gradle 9.x
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`android/local.properties` takes `sdk.dir` and, optionally, `ge.sdl2Dir`.
The native side is the top-level `CMakeLists.txt` (its `ANDROID` branches);
for quick compile iterations without Gradle:
`ANDROID_NDK=<ndk> GE_SDL2_SOURCE_DIR=<sdl2> scripts/android/build-native.sh`.

The APK carries no game data. It is built for one region (`-Pge.romId`,
default `ntsc-final`); only the US ROM is accepted today, as for the PC
release packages.

## First run

`SetupActivity` (the launcher) asks for the player's own ROM through the
system file picker, accepts `.z64`/`.v64`/`.n64` byte orders, checks the
SHA-1 against the README table, copies it to the app's data directory and
runs the PC converters (`tools_pc/d43_emit.py`, `d69_emit.py`,
`d88_emit.py`, unchanged) on the device with an embedded Python
(Chaquopy, `android/app/src/main/python/ge007_convert.py`). The converter
inputs that are not the ROM are a few committed repo files (ROM file lists,
file resource table, model file headers), zipped into the APK at build time.
About 3 s on a PC, a few more on a phone. A stamp file
(`data/.android-convert-stamp`) makes an APK update reconvert.

The game's `data/` directory is
`/sdcard/Android/data/io.github.zeptoniator.ge007/files/data/` (ROM,
sidecars, `ge007.ini`, `ge007.eep` saves, `ge007.log`). Developer shortcut:
`scripts/android/push-data.sh <rom.z64>` converts on the PC and pushes over
adb.

Test launches take arguments from an intent extra:

```sh
adb shell am start -S -n io.github.zeptoniator.ge007/.GoldenEyeActivity --es args "-level_09"
adb logcat -s ge007   # every sysLogPrintf line is mirrored to logcat
```

## Controls

Two button sets, switched by what the game is doing (`inputTouchContext()`
in `port/src/input.c`, polled by the overlay): **playing**, and **menus**
(front end, watch, pause). Icons are vector-drawn; dark discs with light
rings stay readable on snow and night levels.

| Touch | Pad equivalent (port layout) | Shown |
|---|---|---|
| Left 42 % of the screen: floating stick | left stick | always (move / strafe; navigate menus) |
| Drag anywhere else | mouse | always (look: direct look; GEPD-style aim while aiming) |
| FIRE (drag it to aim while firing) | right trigger | playing |
| AIM (toggle, amber ring when on) | left trigger | playing |
| USE | A | playing |
| RELOAD | X | playing |
| NEXT (gun) | Y | playing |
| Watch icon (gadget) | B | playing |
| Crouch (bottom-right corner) | left stick click | playing |
| OK / BACK | A / B | menus |
| Pause icon, or the Back gesture | Start | always (watch / pause) |
| ≡ | Back | always (PC options overlay) |
| Gear | — | always: options (game, touch) |
| Disk | — | always: saves |

**Touch settings** (gear): look sensitivity, vertical look, invert vertical,
button opacity and size, vibration on press, **move buttons** (drag any
button of either set; Reset / Done), defaults. Stored in the app's
`touch` preferences.

The N64 Rumble Pak drives the phone's vibrator. A real controller hides the
overlay and takes over (SDL hot-plug, untested so far).

## Saves at any moment (savestates)

The disk button opens **Saves**: three slots plus an **automatic** one.
Saving works during a solo mission, loading anywhere (front end included).
Leaving the app during a mission writes the automatic slot; at the next
launch the game offers to **resume** it (Android may close a background app
at any time, which used to restart the game from the intro). Files:
`data/states/{auto,slot1..3}.ge7s` (~0.8 MB each). The Bond file saves
(EEPROM) stay the reference: after a load they are re-read from disk, so an
old state never erases mission progress saved since.

`port/src/savestate.c` (Android on by default; `-DGE_SAVESTATE=ON` on Linux):

- **What is saved:** the N64 DRAM arena (8 MB, fixed address in the window)
  and the game's own globals, gathered by `port/savestate.ld` into
  `.ge_state_data`/`.ge_state_bss` (src/game, the engine files with game
  state, libaudio; not init.c/sched.c/joy.c/stacks.c, not the port). zlib,
  written by a background thread.
- **When:** on the main game thread inside its `gfxFrameMsgQ` receive (where
  the F10 watch edits already apply), with the other game threads frozen:
  `portFreezeGameThreads()` (libultra.c) waits until each one is blocked on a
  queue or parked at a checkpoint at the entry/exit of `osRecvMesg`/
  `osSendMesg`, and the render worker has no frame.
- **Load, in two steps:** the saved stage is first loaded the normal way
  (`g_MainStageNum`, as the front end does) so the port's per-stage state
  matches it; at its first frame the saved memory replaces the fresh one.
  Every message queue, thread object and timer of the running process is kept
  (`portForEachKernelObject`). Android loads `libmain.so` at a random address:
  8-byte words that pointed into the saving process's image are moved by the
  difference. Texture cache reset; interpolation treats it as a cut.
- **Limits:** solo only; a state loads only in the build that wrote it
  (layout fingerprint).
- **Test hooks:** `--ss-save <file> [--ss-save-tick n]`,
  `--ss-load <file> [--ss-load-tick n]`. Verified headless on Linux: Bunker 1,
  scripted walk through the door and four shots, saved, then a new process
  from the title loads it: same place, 3 rounds left, same bullet holes, runs
  on.

## Game options

In the gear panel, section **Game**: **Invincibility**, **Infinite ammo**,
**All guns** (the game's own cheats, switched on through its in-mission
cheat path and re-applied at each stage start; switching one off only
removes what the panel turned on), **Auto-aim** (the Bond file's watch
setting) and **Unlock everything** (the port's `Game.AllUnlocked`: every
level, 00 Agent, 007 mode and the game's Cheat menu). The difficulty is
chosen at mission select, as on the N64. All requests are applied on the game
thread (`port/android/android_game.c`, the same rule as watchsettings.c).

## How it works (what differs from Linux)

- **Address window.** The game stores addresses in 32-bit fields, so the port
  keeps a 4 GiB "N64 window" (`port/include/portaddr.h`). Linux puts it at 0;
  an Android app process cannot (ART's heap and boot image live in the low
  4 GiB), so the window moves to `PORT_ADDR_BASE = 0x4000000000` (256 GiB,
  inside a 39-bit user space), the macOS arm64 model with another base.
  `portAddrInit` reserves it `PROT_NONE` with `MAP_FIXED_NOREPLACE`;
  DRAM, cart and stacks are carved inside. `libmain.so` loads at a random
  high address, so pointers into the image use the image-relative encoding,
  as on Windows/macOS (`sysImageBase` = `dladdr`).
- **Absolute symbols.** `romassets_<r>.s` and `dram_syms.S` are re-emitted
  with the base added and every symbol `.hidden`
  (`scripts/android/gen_elf_syms.py`); `port/android/libmain.map` keeps all
  symbols local. lld then writes these SHN_ABS values into the GOT at link
  time: a preemptible absolute symbol would get bionic's load bias added.
- **clang.** The NDK has no GCC. `-fms-extensions` gives the decomp's
  `inherits Base;` idiom (base always first, so Plan 9 pointer conversion is
  not needed); GCC's `-fpermissive` becomes a list of `-Wno-error=`. lv.c and
  spectrum.c are compiled from copies where `#pragma weak A = B` is a macro
  (`scripts/android/weak_pragmas_to_macros.py`; clang makes a second,
  ambiguous declaration). C++ sources get `-nostdinc` plus an explicit
  libc++/clang/bionic include order ahead of the game's `-I` list, since
  libc++ reaches the C headers with a plain `#include` and the decomp's own
  `<math.h>` would shadow them. `_FORTIFY_SOURCE` is off (bionic's fortify
  headers need `SSIZE_MAX` from the shadowed `<limits.h>`).
- **bionic.** `memfd_create` by syscall (minSdk 26), `backtrace` provided by
  `port/android/android_execinfo.c` (bionic has it from API 33), `ntohl` from
  `<sys/endian.h>`.
- **Process.** `SDL_main` (`port/android/android_main.c`) enters the data
  directory and calls the port's `main()` (`portMain`), then `exit()`s:
  Android would otherwise rerun the non-reentrant game in the same process.
- **Video.** ES 3.0 context first, `SDL_WINDOW_FULLSCREEN_DESKTOP` for
  immersive mode, landscape both ways (`SDL_ENV.SDL_IOS_ORIENTATIONS`
  in the manifest; the resizable window would otherwise allow portrait).
  glad still logs the desktop-only GL entry points it cannot find; the ES
  path does not call them (`glDepthRangef` is used).
- **Input.** `port/android/android_touch.c` attaches an SDL virtual game
  controller before `inputOpenPads()` and feeds the look drag to
  `inputUpdate()` as grabbed-mouse motion; Android joins macOS in starting
  with the mouse armed (no click-to-lock). Touch never becomes SDL mouse
  clicks (`SDL_TOUCH_MOUSE_EVENTS=0`). Back is an `OnBackInvokedCallback`
  (Android 16 delivers no `KEYCODE_BACK` to apps targeting SDK 36+).

## Open items

- A touch-friendly F10 (PC options) overlay; a left-hand fire button.
- Split-screen with several real controllers; touch as one seat of a local
  match.
- PAL / JP builds (`-Pge.romId`) and per-region SHA-1s in `SetupActivity`.
- A whole-campaign pass on a phone, and a mid-range Adreno/Mali device.
- APK licence notices screen (SDL2 zlib, CPython PSF, Chaquopy MIT).
