package io.github.zeptoniator.ge007;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.window.OnBackInvokedDispatcher;
import android.view.WindowManager;

import android.util.Log;

import org.libsdl.app.SDL;
import org.libsdl.app.SDLActivity;

/**
 * The game activity: SDL's activity running libmain.so (the PC port's
 * main(), see port/android/android_main.c), with the on-screen controls
 * (TouchOverlay) on top of SDL's surface.
 *
 * Launch arguments for testing come from the "args" extra, split on spaces:
 *   adb shell am start -n io.github.zeptoniator.ge007/.GoldenEyeActivity --es args "-level_09"
 */
public class GoldenEyeActivity extends SDLActivity implements InputManager.InputDeviceListener {

    private static Vibrator vibrator;
    private TouchOverlay overlay;
    private TouchSettingsPanel settings;
    private SaveStatesPanel saves;
    private InputManager inputManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SetupActivity.dataReady(this)) {
            // No ROM / sidecars yet: SDL has not started the game thread
            // (that waits for onResume), so hand over to the setup screen.
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        if (mLayout != null) {
            overlay = new TouchOverlay(this);
            mLayout.addView(overlay, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            settings = new TouchSettingsPanel(this, overlay);
            mLayout.addView(settings, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            saves = new SaveStatesPanel(this, overlay);
            mLayout.addView(saves, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            overlay.setListener(new TouchOverlay.Listener() {
                @Override
                public void onOpenSettings() {
                    settings.show();
                }

                @Override
                public void onOpenSaves() {
                    saves.show();
                }
            });
            GameOptions.applySaved(this);
            // A recent automatic state even if the app is killed without warning.
            SaveStates.nativeSetAuto(SaveStates.file(this, SaveStates.AUTO).getPath(), 45);
            offerResume();
            inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
            inputManager.registerInputDeviceListener(this, new Handler(Looper.getMainLooper()));
            updateOverlayForControllers();
        }
        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13+ delivers Back (key or gesture) here, and from
            // Android 16 (target SDK 36+) only here: no KEYCODE_BACK event.
            // Back must not leave the game; it opens the watch (Start).
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::backPressed);
        }
    }

    private final Handler ui = new Handler(Looper.getMainLooper());

    /** Back = a short Start press (the watch / pause menu). */
    private void backPressed() {
        if (settings != null && settings.isOpen()) {
            settings.hide();
            return;
        }
        if (saves != null && saves.isOpen()) {
            saves.hide();
            return;
        }
        if (overlay != null && overlay.isEditing()) {
            overlay.setEditMode(false);
            return;
        }
        if (overlay == null || overlay.getVisibility() != android.view.View.VISIBLE) {
            return; // a real pad is in use: its own Start button pauses
        }
        overlay.pressStart(true);
        ui.postDelayed(() -> overlay.pressStart(false), 120);
    }

    // ------------------------------------------- autosave / resume

    @Override
    protected void onPause() {
        // Leaving the screen during a mission: keep it in the automatic slot,
        // offered back if the app is started again (Android may close a
        // background app at any time). Done before SDL pauses the game.
        if (overlay != null) {
            if (SaveStates.nativeCanSave()) {
                SaveStates.saveAndWait(this, SaveStates.AUTO, 2500);
            } else if (!SaveStates.nativeBusy() && !restarting) {
                // Not in a mission (front end): nothing to resume later.
                //noinspection ResultOfMethodCallIgnored
                SaveStates.file(this, SaveStates.AUTO).delete();
            }
        }
        super.onPause();
    }

    /** At launch: a mission left by closing the app can be resumed. */
    private void offerResume() {
        java.io.File auto = SaveStates.file(this, SaveStates.AUTO);
        long[] info = SaveStates.nativePeek(auto.getPath());
        if (info == null || info[0] != 1) {
            return;
        }
        String what = SaveStates.describe(this, info);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.ss_resume_title)
                .setMessage(what)
                .setPositiveButton(R.string.ss_resume, (d, w) -> SaveStates.nativeLoad(auto.getPath()))
                .setNegativeButton(R.string.ss_resume_no, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (inputManager != null) {
            inputManager.unregisterInputDeviceListener(this);
        }
        super.onDestroy();
    }

    /**
     * Options panel, "Change ROM": the game cannot be unloaded from this
     * process, so restart the app into SetupActivity (pick a ROM or switch
     * region), keeping a mission in progress in the automatic save.
     */
    private boolean restarting;

    void changeRom() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.opt_change_rom_confirm)
                .setNegativeButton(R.string.opt_cancel, null)
                .setPositiveButton(R.string.opt_restart, (d, w) -> {
                    restarting = true;   // onPause must not drop the automatic save
                    if (SaveStates.nativeCanSave()) {
                        SaveStates.saveAndWait(this, SaveStates.AUTO, 2500);
                    }
                    Intent i = new Intent(this, SetupActivity.class)
                            .putExtra(SetupActivity.EXTRA_CHANGE_ROM, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    Runtime.getRuntime().exit(0);
                })
                .show();
    }

    /** The imported ROM's game: libmain.so (US), libmain_pal.so, libmain_jpn.so. */
    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL2", Regions.library(Regions.active(this)) };
    }

    /** port/android/loader/ge_fixedload.c: the library's base, 0 on failure. */
    private static native long nativeLoadMainFixed(String soname);

    /**
     * libmain.so at the same address in every process, so that savestates
     * from an earlier run load without shifting pointers. SDL's own
     * loadLibrary("main") then finds it loaded and registers it with ART.
     */
    @Override
    public void loadLibraries() {
        String main = getLibraries()[1];
        SDL.loadLibrary("SDL2", this);
        try {
            System.loadLibrary("geload");
            if (nativeLoadMainFixed("lib" + main + ".so") == 0) {
                Log.w("ge007", "lib" + main + ".so: normal load (savestates from other runs are relocated)");
            }
        } catch (UnsatisfiedLinkError e) {
            Log.w("ge007", "libgeload.so: " + e.getMessage());
        }
        SDL.loadLibrary(main, this);
    }

    @Override
    protected String[] getArguments() {
        Intent intent = getIntent();
        String args = intent != null ? intent.getStringExtra("args") : null;
        if (args == null || args.trim().isEmpty()) {
            return new String[0];
        }
        return args.trim().split("\\s+");
    }

    // ------------------------------------------------- Back key = Start
    // (Android 12 and older: the key event; newer: backPressed() above.)

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && overlay != null
                && overlay.getVisibility() == android.view.View.VISIBLE
                && (event.getSource() & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD) {
            if (Build.VERSION.SDK_INT < 33) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    overlay.pressStart(true);
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    overlay.pressStart(false);
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // ------------------------------------------ real controllers vs touch

    private static boolean isGameController(InputDevice d) {
        if (d == null || d.isVirtual()) {
            return false;
        }
        int s = d.getSources();
        return (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    /**
     * A real controller hides the on-screen controls (it takes player 1),
     * unless the touch settings keep them: then touch stays player 1 and the
     * controllers are players 2-4 (port/src/input.c, mpRoute).
     */
    void updateOverlayForControllers() {
        boolean pad = false;
        for (int id : InputDevice.getDeviceIds()) {
            if (isGameController(InputDevice.getDevice(id))) {
                pad = true;
                break;
            }
        }
        if (overlay != null) {
            overlay.setActive(!pad || overlay.keptWithControllers());
        }
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        updateOverlayForControllers();
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        updateOverlayForControllers();
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        updateOverlayForControllers();
    }

    // ---------------------------------------------------------- rumble

    /** Called from native code (the virtual pad's rumble): 0 stops. */
    @SuppressWarnings("unused")
    public static void rumble(int strength) {
        Vibrator v = vibrator;
        if (v == null || !v.hasVibrator()) {
            return;
        }
        if (strength <= 0) {
            v.cancel();
            return;
        }
        int amplitude = Math.max(1, Math.min(255, strength >> 8));
        if (Build.VERSION.SDK_INT >= 26) {
            // SDL stops the rumble itself (strength 0) when the game's
            // duration ends; the long one-shot is only an upper bound.
            v.vibrate(VibrationEffect.createOneShot(2000, amplitude));
        } else {
            v.vibrate(2000);
        }
    }
}
