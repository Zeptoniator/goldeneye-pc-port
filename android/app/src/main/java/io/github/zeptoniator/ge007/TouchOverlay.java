package io.github.zeptoniator.ge007;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.util.SparseArray;
import android.view.DisplayCutout;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

import java.util.ArrayList;
import java.util.List;

/**
 * On-screen controls, drawn over the game's surface.
 *
 * Everything goes to the native side (port/android/android_touch.c):
 * the move stick and the buttons drive an SDL virtual game controller, so
 * the port's Xbox-pad layout applies as is; a drag on the right of the
 * screen is mouse-look. The view consumes every touch, so SDL never turns
 * a tap into a mouse click.
 *
 * Layout (landscape): a floating move stick anywhere on the left 42 % of the
 * screen; the buttons sit in the right-hand bar the 4:3 picture leaves free;
 * any other touch on the right half looks around. Dragging from FIRE also
 * looks, so one thumb can aim and shoot.
 */
public class TouchOverlay extends View {

    // SDL_GameControllerButton / SDL_GameControllerAxis values.
    static final int BTN_A = 0, BTN_B = 1, BTN_X = 2, BTN_Y = 3, BTN_BACK = 4,
            BTN_START = 6, BTN_LEFTSTICK = 7, BTN_LEFTSHOULDER = 9, BTN_RIGHTSHOULDER = 10,
            BTN_DPAD_UP = 11, BTN_DPAD_DOWN = 12, BTN_DPAD_LEFT = 13, BTN_DPAD_RIGHT = 14;
    static final int AXIS_LEFTX = 0, AXIS_LEFTY = 1, AXIS_TRIGGERLEFT = 4, AXIS_TRIGGERRIGHT = 5;

    static native void nativeAxis(int axis, int value);
    static native void nativeButton(int button, boolean down);
    static native void nativeLook(float dx, float dy);
    static native void nativeSetEnabled(boolean on);

    /** A round button. {@code axis} >= 0 makes it a trigger (full deflection when down). */
    private static final class Button {
        final String label;
        final int button;
        final int axis;
        final boolean toggle;     // latches on tap (AIM)
        final boolean looks;      // dragging it also looks (FIRE)
        final float fx, fy, fr;   // dp from the right edge, fraction of the height, radius (dp)
        float cx, cy, r;          // px, computed in layout()
        boolean down;             // current output state
        int pointer = -1;

        Button(String label, int button, int axis, boolean toggle, boolean looks,
               float fromRight, float yFraction, float radius) {
            this.label = label;
            this.button = button;
            this.axis = axis;
            this.toggle = toggle;
            this.looks = looks;
            this.fx = fromRight;
            this.fy = yFraction;
            this.fr = radius;
        }

        boolean hit(float x, float y) {
            float dx = x - cx, dy = y - cy;
            float rr = r * 1.15f; // a little slack around the drawn circle
            return dx * dx + dy * dy <= rr * rr;
        }
    }

    private final List<Button> buttons = new ArrayList<>();
    private final float dp;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Move stick (floating).
    private int stickPointer = -1;
    private float stickCx, stickCy, stickX, stickY;
    private float stickRadius;
    private float stickZoneRight;     // px: touches left of this start the stick

    // Look drags: pointer id -> last position.
    private final SparseArray<float[]> lookers = new SparseArray<>();
    /** Look sensitivity: N64-port mouse pixels per screen pixel. */
    private float lookScale = 2.5f;

    private int safeLeft, safeRight;
    private final List<Rect> cutouts = new ArrayList<>();
    private boolean active = true;

    public TouchOverlay(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        fill.setStyle(Paint.Style.FILL);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2 * dp);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);

        // Right-hand cluster: two columns in the bar a 4:3 picture leaves free
        // on a phone (x in dp from the right edge, y as a fraction of the
        // height), FIRE big and low under the thumb, AIM just left of it.
        buttons.add(new Button("START", BTN_START, -1, false, false, 44, 0.085f, 23));
        buttons.add(new Button("MENU", BTN_BACK, -1, false, false, 118, 0.085f, 20));
        buttons.add(new Button("GUN", BTN_Y, -1, false, false, 44, 0.265f, 28));
        buttons.add(new Button("GADGET", BTN_B, -1, false, false, 118, 0.255f, 25));
        buttons.add(new Button("RELOAD", BTN_X, -1, false, false, 44, 0.445f, 28));
        buttons.add(new Button("USE", BTN_A, -1, false, false, 118, 0.435f, 31));
        buttons.add(new Button("FIRE", -1, AXIS_TRIGGERRIGHT, false, true, 82, 0.68f, 50));
        // CROUCH clear of the ammo counter, which the game draws bottom right.
        buttons.add(new Button("CROUCH", BTN_LEFTSTICK, -1, false, false, 186, 0.6f, 25));
        buttons.add(new Button("AIM", -1, AXIS_TRIGGERLEFT, true, false, 190, 0.83f, 34));

        setOnApplyWindowInsetsListener((v, insets) -> {
            readCutout(insets);
            return insets;
        });
    }

    /** Show or hide the controls (hidden while a real controller is connected). */
    public void setActive(boolean on) {
        if (active == on) {
            return;
        }
        active = on;
        releaseAll();
        setVisibility(on ? VISIBLE : GONE);
        nativeSetEnabled(on);
    }

    public void setLookScale(float s) {
        lookScale = s;
    }

    private void readCutout(WindowInsets insets) {
        safeLeft = safeRight = 0;
        cutouts.clear();
        if (Build.VERSION.SDK_INT >= 28) {
            DisplayCutout c = insets.getDisplayCutout();
            if (c != null) {
                safeLeft = c.getSafeInsetLeft();
                safeRight = c.getSafeInsetRight();
                cutouts.addAll(c.getBoundingRects());
            }
        }
        layoutControls(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutControls(w, h);
    }

    private void layoutControls(int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        for (Button b : buttons) {
            b.r = b.fr * dp;
            b.cx = w - b.fx * dp;
            b.cy = b.fy * h;
            // A punch-hole camera sits on a short edge in landscape: push only
            // the buttons that would overlap it, sideways, out of its way.
            for (Rect r : cutouts) {
                float pad = 6 * dp;
                if (b.cy + b.r > r.top - pad && b.cy - b.r < r.bottom + pad
                        && b.cx + b.r > r.left - pad && b.cx - b.r < r.right + pad) {
                    if (r.centerX() > w / 2) {
                        b.cx = r.left - pad - b.r;
                    } else {
                        b.cx = r.right + pad + b.r;
                    }
                }
            }
        }
        stickRadius = 68 * dp;
        stickZoneRight = w * 0.42f;
        text.setTextSize(11 * dp);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!active) {
            return false;
        }
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                pointerDown(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) {
                    pointerMove(e.getPointerId(i), e.getX(i), e.getY(i));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                pointerUp(e.getPointerId(idx));
                break;
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    private void pointerDown(int id, float x, float y) {
        for (Button b : buttons) {
            if (b.pointer < 0 && b.hit(x, y)) {
                b.pointer = id;
                if (b.toggle) {
                    setButton(b, !b.down);
                } else {
                    setButton(b, true);
                }
                if (b.looks) {
                    lookers.put(id, new float[] { x, y });
                }
                return;
            }
        }
        if (stickPointer < 0 && x < stickZoneRight) {
            stickPointer = id;
            // Keep the whole base on screen.
            stickCx = Math.max(stickRadius, Math.min(x, stickZoneRight - stickRadius * 0.25f));
            stickCy = Math.max(stickRadius, Math.min(y, getHeight() - stickRadius));
            stickX = x;
            stickY = y;
            sendStick();
            return;
        }
        lookers.put(id, new float[] { x, y });
    }

    private void pointerMove(int id, float x, float y) {
        if (id == stickPointer) {
            stickX = x;
            stickY = y;
            sendStick();
            return;
        }
        float[] last = lookers.get(id);
        if (last != null) {
            float dx = x - last[0], dy = y - last[1];
            last[0] = x;
            last[1] = y;
            if (dx != 0 || dy != 0) {
                nativeLook(dx * lookScale, dy * lookScale);
            }
        }
    }

    private void pointerUp(int id) {
        if (id == stickPointer) {
            stickPointer = -1;
            nativeAxis(AXIS_LEFTX, 0);
            nativeAxis(AXIS_LEFTY, 0);
        }
        lookers.remove(id);
        for (Button b : buttons) {
            if (b.pointer == id) {
                b.pointer = -1;
                if (!b.toggle) {
                    setButton(b, false);
                }
            }
        }
    }

    private void releaseAll() {
        stickPointer = -1;
        nativeAxis(AXIS_LEFTX, 0);
        nativeAxis(AXIS_LEFTY, 0);
        lookers.clear();
        for (Button b : buttons) {
            b.pointer = -1;
            setButton(b, false);
        }
        invalidate();
    }

    private void sendStick() {
        float dx = (stickX - stickCx) / stickRadius;
        float dy = (stickY - stickCy) / stickRadius;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > 1f) {
            dx /= len;
            dy /= len;
        }
        nativeAxis(AXIS_LEFTX, Math.round(dx * 32767f));
        nativeAxis(AXIS_LEFTY, Math.round(dy * 32767f));
    }

    private void setButton(Button b, boolean down) {
        if (b.down == down) {
            return;
        }
        b.down = down;
        if (b.axis >= 0) {
            nativeAxis(b.axis, down ? 32767 : 0);
        } else {
            nativeButton(b.button, down);
        }
        if (down) {
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    /** Back key: Start (the watch / pause menu). */
    public void pressStart(boolean down) {
        nativeButton(BTN_START, down);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas c) {
        if (!active) {
            return;
        }
        // Move stick: a faint hint where the thumb usually lands, or the live stick.
        float bx, by;
        boolean live = stickPointer >= 0;
        if (live) {
            bx = stickCx;
            by = stickCy;
        } else {
            bx = Math.max(safeLeft, 0) + 120 * dp;
            by = getHeight() - 110 * dp;
        }
        fill.setColor(0x22FFFFFF);
        c.drawCircle(bx, by, stickRadius, fill);
        ring.setColor(live ? 0x88FFFFFF : 0x44FFFFFF);
        c.drawCircle(bx, by, stickRadius, ring);
        float kx = bx, ky = by;
        if (live) {
            float dx = stickX - stickCx, dy = stickY - stickCy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > stickRadius) {
                dx = dx / len * stickRadius;
                dy = dy / len * stickRadius;
            }
            kx += dx;
            ky += dy;
        }
        fill.setColor(live ? 0x99FFFFFF : 0x44FFFFFF);
        c.drawCircle(kx, ky, 30 * dp, fill);

        for (Button b : buttons) {
            boolean on = b.down;
            fill.setColor(on ? 0x88FFFFFF : 0x30FFFFFF);
            c.drawCircle(b.cx, b.cy, b.r, fill);
            ring.setColor(on ? 0xEEFFFFFF : 0x77FFFFFF);
            c.drawCircle(b.cx, b.cy, b.r, ring);
            text.setColor(on ? 0xFF000000 : 0xCCFFFFFF);
            float ts = b.r < 26 * dp ? 9 * dp : 11 * dp;
            text.setTextSize(ts);
            Rect bounds = new Rect();
            text.getTextBounds(b.label, 0, b.label.length(), bounds);
            c.drawText(b.label, b.cx, b.cy - bounds.exactCenterY(), text);
        }
    }
}
