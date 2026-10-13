# Android port (arm64, experimental)

The PC port runs on Android as `libmain.so` inside SDL2's Java activity,
with on-screen controls and a first-run ROM import. Same game code, same
port layer, same renderer (OpenGL ES 3.0 through fast3d's ES path).

**Verified (2026-10-13, Samsung SM-S948B, Android 17, Adreno 840):** boot, intro, the front end driven by touch (title, file, mission,
difficulty, briefing) into Dam with its intro cutscene, `-level_09`
(Bunker 1) played by touch (move, look, use a door, fire), the watch on
Back, background/resume, on-device asset conversion byte-identical to the
PC converters; savestates: save from the panel, Home then a killed app
then "Resume" back into the mission, saves after a load, the periodic
automatic save; F10 by touch (open, tap a page, drag a slider), a 2-player
split-screen match with touch as player 1 and a virtual controller as
player 2 (`--test-pads 1 --setenv GE_STARTMP=2,1`), Change ROM and the
licences screen. **On an emulator (Android 36 x86_64, ARM translation):**
the PAL ROM picked in the system file picker, converted on the device, and
`libmain_pal.so` booting at the fixed address. 120 Hz presents with frame interpolation (99-120/s on Dam);
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

The APK carries no game data. It carries the game once per ROM region:
`libmain.so` (US, built by the app module), `libmain_pal.so` and
`libmain_jpn.so` (the `android/region-pal` and `region-jpn` library
modules: same CMake project, another `ROMID`; `android/region-lib.gradle`).
`-Pge.regions=ntsc-final` builds the US one only, for quicker iterations.
PAL and JP are at the PC port's own level for those regions (they convert,
build and boot; ROADMAP §1 owes their full runtime passes). 2026-10-13, PAL
on Linux (same game code as `libmain_pal.so`): each of the 21 solo levels
booted with `-level_NN` and ran 40 s headless without a crash.

## First run

`SetupActivity` (the launcher) asks for the player's own ROM through the
system file picker, accepts `.z64`/`.v64`/`.n64` byte orders, checks the
SHA-1 against the README table (US, Europe or Japan: the hash gives the
region, `Regions.java`), copies it to the app's data directory and
runs the PC converters (`tools_pc/d43_emit.py`, `d69_emit.py`,
`d88_emit.py`, unchanged) on the device with an embedded Python
(Chaquopy, `android/app/src/main/python/ge007_convert.py`). The converter
inputs that are not the ROM are a few committed repo files (ROM file lists,
file resource table, model file headers), zipped into the APK at build time.
About 3 s on a PC, a few more on a phone. A stamp file
(`data/.android-convert-stamp`, `-<region>` for PAL/JP) makes an APK update
reconvert. Each region keeps its own ROM and sidecars side by side; the last
one imported is the active region, whose library `GoldenEyeActivity` loads.
Gear -> **Change ROM...** restarts the app into `SetupActivity` (a game
cannot be unloaded from its process): pick another ROM, or play a region
imported before. **Licences** (there and on the setup screen) shows
`assets/licences.txt`, put together at build time from `LICENSE`, `NOTICE`,
`port/fast3d/LICENSE.txt`, SDL2's licence and `android/licences/` (CPython,
the software it incorporates, Chaquopy, the runtime's other libraries).

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

An app gets no environment from adb, so `--setenv NAME=VALUE` (repeatable)
sets one before the game starts: the PC harnesses work on the phone, e.g.
`--setenv GE_STARTMP=2,0 --setenv GE_MPVIRT=1` for a 2-player match.
`--test-pads N` attaches N idle virtual controllers that take seats like
real ones.

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

**F10 (≡) by touch:** while the PC options overlay is open only the corner
buttons stay; the panel is touched directly. A tap clicks where it lands
(the overlay's own mouse handling: a setting's name selects it, the area
right of it changes it), a vertical swipe steps through the list, a
horizontal swipe drags a slider. Touch is fed as an absolute pointer
(`androidTouchPointer`, read by `optionsoverlay.c` in place of
`SDL_GetMouseState`); a press is held for at least one poll so a short tap
still clicks.

**Options panel** (gear), drawn over the running game: a rail of three
sections on the left (Game, Controls, App) with **Resume** at its foot under
the left thumb, the chosen section on the right. Game: the cheats and game
options below. Controls: look sensitivity, vertical look, invert vertical,
button opacity and size, vibration on press, keep touch with controllers,
**move buttons** (drag any button of either set; Reset / Done), defaults.
App: the ROM in use, Change ROM, Licences. It reopens on the last section
used. Touch settings live in the app's `touch` preferences. The look,
"Opening Titles" (`Titles.java`, `android/DESIGN.md`): near-black, ivory
text, gold only for what is on or active, Michroma (OFL) for the section
titles; switching section replays the title like an opening credit.

The N64 Rumble Pak drives the phone's vibrator.

**Controllers and split-screen.** By default a real controller hides the
overlay and takes player 1; with several, they are players 1-4 in
connection order (the PC port's seats, `input.c`), and the game's
multiplayer menu opens with 2+. With **Keep touch with controllers** on,
the on-screen pad stays in seat 0 and its look drag rides on the
keyboard/mouse block of the same slot: touch is player 1, controllers are
players 2-4 (`mpRoute` returns no shift while the on-screen pad holds seat
0; outside a match the pad is moved back to seat 0). Touch alone counts as
one controller (it used to count as two: keyboard/mouse plus the pad).

## Saves at any moment (savestates)

The disk button opens **Saves**: three slots plus an **automatic** one.
Saving works during a solo mission, loading anywhere (front end included).
The automatic slot is written every 45 s of a mission and when the app
leaves the screen; at the next launch the game offers to **resume** it. It
is dropped only when the app leaves the screen in the front end after a
solo mission ran in that session (that mission is over); opening the app
and leaving again keeps it (Android may close a background app
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
  the F10 watch edits already apply), with the other game threads frozen at
  the top of their loops: `portFreezeGameThreads()` (libultra.c) waits until
  each one is blocked on, or parked at, its *home* queue (the first queue it
  ever blocked on: the audio thread between two audio frames, never with a
  frame's completion pending), then lets the scheduler stop anywhere, and
  the render worker has no frame. A periodic save allows 60 ms for this and
  retries 5 s later; leaving the screen allows 1.2 s.
- **Load, in two steps:** the saved stage is first loaded the normal way
  (`g_MainStageNum`, as the front end does) so the port's per-stage state
  matches it; at its first frame the saved memory replaces the fresh one.
  Every message queue, thread object and timer of the running process is kept
  (`portForEachKernelObject`). Texture cache reset; interpolation treats it
  as a cut.
- **Same image address in every process.** The state holds native pointers
  into the game's image (function pointers, the audio driver's lists).
  `libgeload.so` (`port/android/loader/`, called from
  `GoldenEyeActivity.loadLibraries()` before SDL's own load) reserves
  `0x42_0000_0000` and loads `libmain.so` there with `android_dlopen_ext`
  (`ANDROID_DLEXT_RESERVED_ADDRESS`); ART's `loadLibrary("main")` then finds
  it loaded. If that ever fails, the load falls back to shifting every word
  that looks like a pointer into the old image, a heuristic: it once rewrote
  the padding after `ALEvent.type` (stale pointer bytes), the music sequence
  player lost its self-perpetuating event and spun in `__CSPVoiceHandler`,
  and no further save could freeze the audio thread.
- **Limits:** solo only; a state loads only in the build that wrote it
  (layout fingerprint plus the image's GNU build-id).
- **Test hooks:** `--ss-save <file> [--ss-save-tick n]`,
  `--ss-load <file> [--ss-load-tick n]`. Verified headless on Linux: Bunker 1,
  scripted walk through the door and four shots, saved, then a new process
  from the title loads it: same place, 3 rounds left, same bullet holes, runs
  on. `-DGE_HIGHIMAGE=ON` (Linux, with `GE_SAVESTATE`) builds Android's
  address model on the desktop: the window at 256 GiB and the game as a PIE
  at a random address, for chasing Android-only address bugs under gdb
  (`setarch -R` gives the same address every run).

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

- A left-hand fire button.
- Split-screen with real Bluetooth/USB controllers on a phone (verified with
  virtual ones only).
- PAL / JP runtime passes (as for the PC builds); JP not run on a phone.
- A whole-campaign pass on a phone, and a mid-range Adreno/Mali device.
