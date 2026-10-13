package io.github.zeptoniator.ge007;

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
        if (overlay == null || overlay.getVisibility() != android.view.View.VISIBLE) {
            return; // a real pad is in use: its own Start button pauses
        }
        overlay.pressStart(true);
        ui.postDelayed(() -> overlay.pressStart(false), 120);
    }

    @Override
    protected void onDestroy() {
        if (inputManager != null) {
            inputManager.unregisterInputDeviceListener(this);
        }
        super.onDestroy();
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL2", "main" };
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

    private void updateOverlayForControllers() {
        boolean pad = false;
        for (int id : InputDevice.getDeviceIds()) {
            if (isGameController(InputDevice.getDevice(id))) {
                pad = true;
                break;
            }
        }
        if (overlay != null) {
            overlay.setActive(!pad);
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
