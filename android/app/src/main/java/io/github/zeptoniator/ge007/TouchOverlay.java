package io.github.zeptoniator.ge007;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.SparseArray;
import android.view.DisplayCutout;
import android.view.HapticFeedbackConstants;
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
 * the port's Xbox-pad layout applies as is; a drag anywhere else on the
 * screen is mouse-look. The view consumes every touch, so SDL never turns
 * a tap into a mouse click.
 *
 * Two button sets, switched by what the game is doing (nativeContext):
 * playing (FIRE, AIM, USE, RELOAD, NEXT gun, gadget, CROUCH) and menus
 * (front end, watch, pause: OK, BACK). Both have the floating move stick on
 * the left 42 % of the screen, START, the PC options MENU and the settings
 * gear. The playing set is an arc around FIRE under the right thumb;
 * dragging from FIRE also looks, so one thumb can aim and shoot.
 *
 * The gear opens TouchSettingsPanel (sensitivity, opacity, size, haptics,
 * layout editing). Settings and moved buttons persist in the "touch"
 * SharedPreferences.
 */
public class TouchOverlay extends View {

    // SDL_GameControllerButton / SDL_GameControllerAxis values.
    static final int BTN_A = 0, BTN_B = 1, BTN_X = 2, BTN_Y = 3, BTN_BACK = 4,
            BTN_START = 6, BTN_LEFTSTICK = 7, BTN_LEFTSHOULDER = 9, BTN_RIGHTSHOULDER = 10,
            BTN_DPAD_UP = 11, BTN_DPAD_DOWN = 12, BTN_DPAD_LEFT = 13, BTN_DPAD_RIGHT = 14;
    static final int AXIS_LEFTX = 0, AXIS_LEFTY = 1, AXIS_TRIGGERLEFT = 4, AXIS_TRIGGERRIGHT = 5;
    /** Not pad buttons: open the settings / the savestate panel. */
    private static final int BTN_SETTINGS = -2, BTN_SAVES = -3;

    static native void nativeAxis(int axis, int value);
    static native void nativeButton(int button, boolean down);
    static native void nativeLook(float dx, float dy);
    static native void nativeSetEnabled(boolean on);
    /** 0 = front end, 1 = playing, 2 = watch / pause (port/src/input.c). */
    static native int nativeContext();

    static final String PREFS = "touch";
    /** Base look gain: port mouse pixels per screen pixel at 100 %. */
    private static final float LOOK_BASE = 2.5f;

    // Which button sets a button belongs to.
    private static final int IN_MENU = 1, IN_GAME = 2, ALWAYS = IN_MENU | IN_GAME;

    // Icons (drawn in drawIcon).
    private static final int IC_BULLET = 1, IC_AIM = 2, IC_HAND = 3, IC_RELOAD = 4, IC_GUN = 5,
            IC_WATCH = 6, IC_CROUCH = 7, IC_PAUSE = 8, IC_MENU = 9, IC_GEAR = 10, IC_OK = 11,
            IC_BACK = 12, IC_DISK = 13;

    /** Opens the settings / savestate panels (GoldenEyeActivity). */
    public interface Listener {
        void onOpenSettings();

        void onOpenSaves();
    }

    /** A round button. {@code axis} >= 0 makes it a trigger (full deflection when down). */
    private static final class Button {
        final String key;         // preference key for a moved position
        final String label;       // small caption under the icon ("" = icon only)
        final int icon;
        final int sets;           // IN_MENU / IN_GAME
        final int button;
        final int axis;
        final boolean toggle;     // latches on tap (AIM)
        final boolean looks;      // dragging it also looks (FIRE)
        final int accent;         // ring colour
        final float fx, fy, fr;   // dp from the right edge (< 0: from the left), fraction of the height, radius (dp)
        float userX = -1, userY = -1; // moved by the player: centre as fractions of the view
        float cx, cy, r;          // px, computed in layoutControls()
        boolean down;             // current output state
        int pointer = -1;

        Button(String key, String label, int icon, int sets, int button, int axis,
               boolean toggle, boolean looks, int accent, float fromRight, float yFraction, float radius) {
            this.key = key;
            this.label = label;
            this.icon = icon;
            this.sets = sets;
            this.button = button;
            this.axis = axis;
            this.toggle = toggle;
            this.looks = looks;
            this.accent = accent;
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
    private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);      // icon strokes
    private final Paint inkFill = new Paint(Paint.ANTI_ALIAS_FLAG);  // icon fills
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final Rect textBounds = new Rect();
    private Listener listener;

    // Move stick (floating).
    private int stickPointer = -1;
    private float stickCx, stickCy, stickX, stickY;
    private float stickRadius;
    private float stickZoneRight;     // px: touches left of this start the stick

    // Look drags: pointer id -> last position.
    private final SparseArray<float[]> lookers = new SparseArray<>();

    // What the game is doing: which set is shown.
    private int context = IN_MENU;
    private final Runnable contextPoll = new Runnable() {
        @Override
        public void run() {
            int c = nativeContext() == 1 ? IN_GAME : IN_MENU;
            if (c != context && !editMode) {
                releaseAll();   // nothing stays held across a set change
                context = c;
                invalidate();
            }
            postDelayed(this, 150);
        }
    };

    // Player settings (TouchSettingsPanel).
    private float lookSens = 1f;      // look gain, x LOOK_BASE
    private float lookSensY = 1f;     // vertical gain relative to horizontal
    private boolean invertY;
    private float opacity = 1f;
    private float sizeScale = 1f;
    private boolean haptics = true;

    // Layout editing (both sets are shown and movable).
    private boolean editMode;
    private Button dragged;
    private int dragPointer = -1;
    private float dragDx, dragDy;
    private final RectF doneRect = new RectF();
    private final RectF resetRect = new RectF();

    private int safeLeft, safeRight;
    private final List<Rect> cutouts = new ArrayList<>();
    private boolean active = true;

    public TouchOverlay(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        fill.setStyle(Paint.Style.FILL);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.6f * dp);
        dash.setStyle(Paint.Style.STROKE);
        dash.setStrokeWidth(2 * dp);
        dash.setPathEffect(new DashPathEffect(new float[] { 6 * dp, 5 * dp }, 0));
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
        inkFill.setStyle(Paint.Style.FILL);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);

        final int red = 0xFFFF5252, amber = 0xFFFFC107, white = 0xFFFFFFFF, green = 0xFF69F0AE;
        // Playing: an arc around FIRE under the right thumb (x in dp from the
        // right edge, y as a fraction of the height). The ammo counter the
        // game draws bottom right stays clear.
        buttons.add(new Button("FIRE", "FIRE", IC_BULLET, IN_GAME, -1, AXIS_TRIGGERRIGHT, false, true, red, 96, 0.70f, 50));
        buttons.add(new Button("AIM", "AIM", IC_AIM, IN_GAME, -1, AXIS_TRIGGERLEFT, true, false, amber, 205, 0.80f, 33));
        buttons.add(new Button("USE", "USE", IC_HAND, IN_GAME, BTN_A, -1, false, false, white, 196, 0.53f, 31));
        buttons.add(new Button("RELOAD", "RELOAD", IC_RELOAD, IN_GAME, BTN_X, -1, false, false, white, 126, 0.37f, 27));
        buttons.add(new Button("GUN", "NEXT", IC_GUN, IN_GAME, BTN_Y, -1, false, false, white, 48, 0.44f, 27));
        // CROUCH in the corner under FIRE, just right of the ammo counter.
        buttons.add(new Button("CROUCH", "", IC_CROUCH, IN_GAME, BTN_LEFTSTICK, -1, false, false, white, 32, 0.92f, 22));
        buttons.add(new Button("GADGET", "", IC_WATCH, IN_GAME, BTN_B, -1, false, false, white, 116, 0.11f, 21));
        // Menus (front end, watch, pause): accept / back where FIRE / AIM are.
        buttons.add(new Button("OK", "OK", IC_OK, IN_MENU, BTN_A, -1, false, false, green, 96, 0.70f, 46));
        buttons.add(new Button("BACK", "BACK", IC_BACK, IN_MENU, BTN_B, -1, false, false, white, 205, 0.80f, 32));
        // Always: START top right; options MENU and the gear top left.
        buttons.add(new Button("START", "", IC_PAUSE, ALWAYS, BTN_START, -1, false, false, white, 44, 0.11f, 22));
        buttons.add(new Button("MENU", "", IC_MENU, ALWAYS, BTN_BACK, -1, false, false, white, -96, 0.11f, 20));
        buttons.add(new Button("SETTINGS", "", IC_GEAR, ALWAYS, BTN_SETTINGS, -1, false, false, white, -42, 0.11f, 20));
        buttons.add(new Button("SAVES", "", IC_DISK, ALWAYS, BTN_SAVES, -1, false, false, white, -150, 0.11f, 20));

        setOnApplyWindowInsetsListener((v, insets) -> {
            readCutout(insets);
            return insets;
        });
        loadSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(contextPoll);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(contextPoll);
        super.onDetachedFromWindow();
    }

    public void setListener(Listener l) {
        listener = l;
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

    private boolean shown(Button b) {
        return editMode || (b.sets & context) != 0;
    }

    // -------------------------------------------------------------- settings

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** (Re)read the player's settings and moved buttons. */
    public void loadSettings() {
        SharedPreferences p = prefs();
        lookSens = p.getInt("lookSens", 100) / 100f;
        lookSensY = p.getInt("lookSensY", 100) / 100f;
        invertY = p.getBoolean("invertY", false);
        opacity = p.getInt("opacity", 100) / 100f;
        sizeScale = p.getInt("size", 100) / 100f;
        haptics = p.getBoolean("haptics", true);
        for (Button b : buttons) {
            b.userX = p.getFloat("x." + b.key, -1f);
            b.userY = p.getFloat("y." + b.key, -1f);
        }
        layoutControls(getWidth(), getHeight());
        invalidate();
    }

    private void saveLayout() {
        SharedPreferences.Editor e = prefs().edit();
        for (Button b : buttons) {
            if (b.userX >= 0) {
                e.putFloat("x." + b.key, b.userX).putFloat("y." + b.key, b.userY);
            } else {
                e.remove("x." + b.key).remove("y." + b.key);
            }
        }
        e.apply();
    }

    /** Put every button back where it was designed to be. */
    public void resetLayout() {
        for (Button b : buttons) {
            b.userX = b.userY = -1;
        }
        saveLayout();
        layoutControls(getWidth(), getHeight());
        invalidate();
    }

    public boolean isEditing() {
        return editMode;
    }

    /** Layout editing: drag buttons; DONE / RESET pills at the top. */
    public void setEditMode(boolean on) {
        releaseAll();
        editMode = on;
        dragged = null;
        dragPointer = -1;
        if (!on) {
            saveLayout();
        }
        invalidate();
    }

    // ---------------------------------------------------------------- layout

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
            b.r = b.fr * dp * sizeScale;
            if (b.userX >= 0) {
                b.cx = b.userX * w;
                b.cy = b.userY * h;
                continue;
            }
            b.cx = b.fx >= 0 ? w - b.fx * dp : -b.fx * dp;
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
        stickRadius = 66 * dp * sizeScale;
        stickZoneRight = w * 0.42f;
        float pw = 120 * dp, ph = 40 * dp, top = 16 * dp;
        doneRect.set(w / 2f + 8 * dp, top, w / 2f + 8 * dp + pw, top + ph);
        resetRect.set(w / 2f - 8 * dp - pw, top, w / 2f - 8 * dp, top + ph);
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
                if (editMode) {
                    editDown(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                } else {
                    pointerDown(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                }
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) {
                    if (editMode) {
                        editMove(e.getPointerId(i), e.getX(i), e.getY(i));
                    } else {
                        pointerMove(e.getPointerId(i), e.getX(i), e.getY(i));
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (editMode) {
                    editUp(e.getPointerId(idx));
                } else {
                    pointerUp(e.getPointerId(idx));
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (editMode) {
                    editUp(dragPointer);
                } else {
                    releaseAll();
                }
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    private void pointerDown(int id, float x, float y) {
        for (Button b : buttons) {
            if (shown(b) && b.pointer < 0 && b.hit(x, y)) {
                if (b.button == BTN_SETTINGS || b.button == BTN_SAVES) {
                    feedback();
                    if (listener != null) {
                        if (b.button == BTN_SETTINGS) {
                            listener.onOpenSettings();
                        } else {
                            listener.onOpenSaves();
                        }
                    }
                    return;
                }
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
                float g = LOOK_BASE * lookSens;
                nativeLook(dx * g, (invertY ? -dy : dy) * g * lookSensY);
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

    /** Let go of everything (and tell the game). */
    public void releaseAll() {
        stickPointer = -1;
        nativeAxis(AXIS_LEFTX, 0);
        nativeAxis(AXIS_LEFTY, 0);
        lookers.clear();
        for (Button b : buttons) {
            b.pointer = -1;
            if (b.button >= 0 || b.axis >= 0) {
                setButton(b, false);
            }
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
            // SDL triggers span the whole axis: released is the minimum, not
            // 0 (0 reads as a half-pulled trigger, past the port's 25 % fire
            // threshold: the gun kept firing after FIRE was let go).
            nativeAxis(b.axis, down ? 32767 : -32768);
        } else {
            nativeButton(b.button, down);
        }
        if (down) {
            feedback();
        }
    }

    private void feedback() {
        if (haptics) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    /** Back key: Start (the watch / pause menu). */
    public void pressStart(boolean down) {
        nativeButton(BTN_START, down);
    }

    // ------------------------------------------------------- layout editing

    private void editDown(int id, float x, float y) {
        if (dragPointer >= 0) {
            return;
        }
        if (doneRect.contains(x, y)) {
            feedback();
            setEditMode(false);
            return;
        }
        if (resetRect.contains(x, y)) {
            feedback();
            resetLayout();
            return;
        }
        for (Button b : buttons) {
            if (b.hit(x, y)) {
                dragged = b;
                dragPointer = id;
                dragDx = b.cx - x;
                dragDy = b.cy - y;
                feedback();
                return;
            }
        }
    }

    private void editMove(int id, float x, float y) {
        if (id != dragPointer || dragged == null) {
            return;
        }
        int w = getWidth(), h = getHeight();
        float cx = Math.max(dragged.r, Math.min(w - dragged.r, x + dragDx));
        float cy = Math.max(dragged.r, Math.min(h - dragged.r, y + dragDy));
        dragged.cx = cx;
        dragged.cy = cy;
        dragged.userX = cx / w;
        dragged.userY = cy / h;
    }

    private void editUp(int id) {
        if (id == dragPointer) {
            dragPointer = -1;
            dragged = null;
        }
    }

    // ---------------------------------------------------------------- drawing

    private int alpha(int argb, float extra) {
        float o = editMode ? 1f : opacity;
        int a = Math.round(((argb >>> 24) & 0xFF) * o * extra);
        return (Math.min(255, a) << 24) | (argb & 0x00FFFFFF);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!active) {
            return;
        }
        if (editMode) {
            c.drawColor(0x66000000);
        } else {
            drawStick(c);
        }
        for (Button b : buttons) {
            if (shown(b)) {
                drawButton(c, b);
            }
        }
        if (editMode) {
            drawPill(c, resetRect, getContext().getString(R.string.touch_default), 0xFF444444);
            drawPill(c, doneRect, getContext().getString(R.string.touch_done), 0xFF2E7D32);
            text.setTextSize(13 * dp);
            text.setColor(0xFFFFFFFF);
            c.drawText(getContext().getString(R.string.touch_edit_hint), getWidth() / 2f,
                    doneRect.bottom + 28 * dp, text);
        }
    }

    private void drawStick(Canvas c) {
        boolean live = stickPointer >= 0;
        float bx, by;
        if (live) {
            bx = stickCx;
            by = stickCy;
        } else {
            // Resting hint where the thumb usually lands.
            bx = Math.max(safeLeft, 0) + 118 * dp;
            by = getHeight() - 108 * dp;
        }
        float k = live ? 1f : 0.6f;
        fill.setColor(alpha(0x50000000, k));
        c.drawCircle(bx, by, stickRadius, fill);
        ring.setColor(alpha(0xB0FFFFFF, k));
        c.drawCircle(bx, by, stickRadius, ring);
        // Four direction chevrons on the base.
        ink.setColor(alpha(0x80FFFFFF, k));
        ink.setStrokeWidth(2.2f * dp);
        float t = stickRadius * 0.8f, a = 7 * dp;
        for (int i = 0; i < 4; i++) {
            float ux = i == 0 ? 1 : i == 1 ? -1 : 0;   // outward direction
            float uy = i == 2 ? 1 : i == 3 ? -1 : 0;
            float px = bx + ux * t, py = by + uy * t;  // chevron tip
            path.reset();
            path.moveTo(px - ux * a - uy * a, py - uy * a + ux * a);
            path.lineTo(px, py);
            path.lineTo(px - ux * a + uy * a, py - uy * a - ux * a);
            c.drawPath(path, ink);
        }
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
        fill.setColor(alpha(live ? 0xD0FFFFFF : 0x70FFFFFF, 1f));
        c.drawCircle(kx, ky, 28 * dp * sizeScale, fill);
    }

    private void drawButton(Canvas c, Button b) {
        boolean on = b.down || (editMode && b == dragged);
        // Dark disc, light ring and icon: readable on snow and on night levels.
        fill.setColor(on ? alpha(0xE6FFFFFF, 1f) : alpha(0x59000000, 1f));
        c.drawCircle(b.cx, b.cy, b.r, fill);
        Paint outline = editMode ? dash : ring;
        outline.setColor(editMode ? 0xFFFFFFFF : alpha((b.accent & 0x00FFFFFF) | 0xC8000000, 1f));
        c.drawCircle(b.cx, b.cy, b.r, outline);
        if (b.toggle && b.down) {
            // A latched AIM gets a second ring so it reads as "on".
            ring.setColor(alpha(b.accent, 1f));
            c.drawCircle(b.cx, b.cy, b.r + 4 * dp, ring);
        }
        int fg = on ? 0xFF101010 : alpha(0xF0FFFFFF, 1f);
        boolean caption = b.label.length() > 0 && b.r >= 26 * dp;
        float iconSize = b.r * (caption ? 0.95f : 1.15f);
        float iy = b.cy - (caption ? b.r * 0.16f : 0);
        drawIcon(c, b.icon, b.cx, iy, iconSize, fg);
        if (caption) {
            text.setColor(fg);
            text.setTextSize(Math.max(7.5f * dp, b.r * 0.24f));
            text.getTextBounds(b.label, 0, b.label.length(), textBounds);
            c.drawText(b.label, b.cx, b.cy + b.r * 0.58f - textBounds.exactCenterY(), text);
        }
    }

    /** Simple line icons in a box of side s centred on (x, y). */
    private void drawIcon(Canvas c, int icon, float x, float y, float s, int color) {
        ink.setColor(color);
        inkFill.setColor(color);
        ink.setStrokeWidth(Math.max(1.8f * dp, s * 0.075f));
        float h = s / 2;
        switch (icon) {
            case IC_BULLET: {
                // A cartridge: pointed bullet over a casing with a rim.
                path.reset();
                path.moveTo(x - 0.17f * h, y + 0.05f * h);
                path.lineTo(x - 0.17f * h, y - 0.25f * h);
                path.quadTo(x - 0.17f * h, y - 0.62f * h, x, y - 0.72f * h);
                path.quadTo(x + 0.17f * h, y - 0.62f * h, x + 0.17f * h, y - 0.25f * h);
                path.lineTo(x + 0.17f * h, y + 0.05f * h);
                path.close();
                c.drawPath(path, inkFill);
                rect.set(x - 0.2f * h, y + 0.13f * h, x + 0.2f * h, y + 0.62f * h);
                c.drawRoundRect(rect, 0.04f * h, 0.04f * h, inkFill);
                rect.set(x - 0.25f * h, y + 0.62f * h, x + 0.25f * h, y + 0.72f * h);
                c.drawRect(rect, inkFill);
                break;
            }
            case IC_AIM: {
                c.drawCircle(x, y, 0.42f * h, ink);
                float a = 0.22f * h, b = 0.7f * h;
                c.drawLine(x - b, y, x - a, y, ink);
                c.drawLine(x + a, y, x + b, y, ink);
                c.drawLine(x, y - b, x, y - a, ink);
                c.drawLine(x, y + a, x, y + b, ink);
                c.drawCircle(x, y, 0.07f * h, inkFill);
                break;
            }
            case IC_HAND: {
                // Open hand: palm, four fingers, thumb.
                rect.set(x - 0.34f * h, y - 0.05f * h, x + 0.3f * h, y + 0.62f * h);
                c.drawRoundRect(rect, 0.2f * h, 0.2f * h, inkFill);
                float fw = 0.13f * h;
                float[] tops = { -0.5f, -0.68f, -0.62f, -0.42f };
                for (int i = 0; i < 4; i++) {
                    float fx = x - 0.27f * h + i * 0.17f * h;
                    rect.set(fx - fw / 2, y + tops[i] * h, fx + fw / 2, y + 0.1f * h);
                    c.drawRoundRect(rect, fw / 2, fw / 2, inkFill);
                }
                path.reset();
                path.moveTo(x + 0.22f * h, y + 0.2f * h);
                path.lineTo(x + 0.55f * h, y - 0.12f * h);
                ink.setStrokeWidth(0.14f * h);
                c.drawPath(path, ink);
                break;
            }
            case IC_RELOAD: {
                float r = 0.48f * h;
                rect.set(x - r, y - r, x + r, y + r);
                c.drawArc(rect, -60, 290, false, ink);
                // Arrowhead at the arc's start.
                double ang = Math.toRadians(-60);
                float ex = x + (float) Math.cos(ang) * r, ey = y + (float) Math.sin(ang) * r;
                path.reset();
                path.moveTo(ex + 0.28f * h, ey - 0.02f * h);
                path.lineTo(ex - 0.06f * h, ey - 0.18f * h);
                path.lineTo(ex + 0.04f * h, ey + 0.24f * h);
                path.close();
                c.drawPath(path, inkFill);
                break;
            }
            case IC_GUN: {
                // Pistol facing left, and a small "next" chevron.
                path.reset();
                path.moveTo(x - 0.7f * h, y - 0.35f * h);
                path.lineTo(x + 0.4f * h, y - 0.35f * h);
                path.lineTo(x + 0.4f * h, y - 0.05f * h);
                path.lineTo(x + 0.25f * h, y - 0.05f * h);
                path.lineTo(x + 0.38f * h, y + 0.5f * h);
                path.lineTo(x + 0.08f * h, y + 0.5f * h);
                path.lineTo(x - 0.02f * h, y - 0.05f * h);
                path.lineTo(x - 0.7f * h, y - 0.05f * h);
                path.close();
                c.drawPath(path, inkFill);
                path.reset();
                path.moveTo(x + 0.55f * h, y - 0.05f * h);
                path.lineTo(x + 0.75f * h, y + 0.15f * h);
                path.lineTo(x + 0.55f * h, y + 0.35f * h);
                c.drawPath(path, ink);
                break;
            }
            case IC_WATCH: {
                // Wristwatch: straps above and below a round face, two hands.
                rect.set(x - 0.2f * h, y - 0.78f * h, x + 0.2f * h, y - 0.4f * h);
                c.drawRect(rect, inkFill);
                rect.set(x - 0.2f * h, y + 0.4f * h, x + 0.2f * h, y + 0.78f * h);
                c.drawRect(rect, inkFill);
                c.drawCircle(x, y, 0.42f * h, ink);
                c.drawLine(x, y, x, y - 0.24f * h, ink);
                c.drawLine(x, y, x + 0.18f * h, y + 0.06f * h, ink);
                break;
            }
            case IC_CROUCH: {
                // Two chevrons pressing down onto a floor line.
                for (int i = 0; i < 2; i++) {
                    float yy = y - 0.5f * h + i * 0.32f * h;
                    path.reset();
                    path.moveTo(x - 0.4f * h, yy);
                    path.lineTo(x, yy + 0.3f * h);
                    path.lineTo(x + 0.4f * h, yy);
                    c.drawPath(path, ink);
                }
                c.drawLine(x - 0.5f * h, y + 0.5f * h, x + 0.5f * h, y + 0.5f * h, ink);
                break;
            }
            case IC_PAUSE: {
                rect.set(x - 0.32f * h, y - 0.42f * h, x - 0.1f * h, y + 0.42f * h);
                c.drawRoundRect(rect, 0.05f * h, 0.05f * h, inkFill);
                rect.set(x + 0.1f * h, y - 0.42f * h, x + 0.32f * h, y + 0.42f * h);
                c.drawRoundRect(rect, 0.05f * h, 0.05f * h, inkFill);
                break;
            }
            case IC_MENU: {
                for (int i = -1; i <= 1; i++) {
                    c.drawLine(x - 0.45f * h, y + i * 0.3f * h, x + 0.45f * h, y + i * 0.3f * h, ink);
                }
                break;
            }
            case IC_GEAR: {
                // Eight teeth: alternate outer and inner radius every 1/16 turn.
                float ro = 0.62f * h, ri = 0.44f * h;
                path.reset();
                for (int i = 0; i < 16; i++) {
                    double a0 = Math.PI * 2 * i / 16, a1 = a0 + Math.PI * 2 / 16;
                    float rr = (i % 2 == 0) ? ro : ri;
                    float x0 = x + (float) Math.cos(a0) * rr, y0 = y + (float) Math.sin(a0) * rr;
                    float x1 = x + (float) Math.cos(a1) * rr, y1 = y + (float) Math.sin(a1) * rr;
                    if (i == 0) {
                        path.moveTo(x0, y0);
                    } else {
                        path.lineTo(x0, y0);
                    }
                    path.lineTo(x1, y1);
                }
                path.close();
                path.addCircle(x, y, 0.2f * h, Path.Direction.CCW);
                path.setFillType(Path.FillType.EVEN_ODD);
                c.drawPath(path, inkFill);
                path.setFillType(Path.FillType.WINDING);
                break;
            }
            case IC_DISK: {
                // Floppy disk: body with a notched corner, shutter, label.
                path.reset();
                path.moveTo(x - 0.55f * h, y - 0.55f * h);
                path.lineTo(x + 0.35f * h, y - 0.55f * h);
                path.lineTo(x + 0.55f * h, y - 0.35f * h);
                path.lineTo(x + 0.55f * h, y + 0.55f * h);
                path.lineTo(x - 0.55f * h, y + 0.55f * h);
                path.close();
                rect.set(x - 0.3f * h, y - 0.55f * h, x + 0.22f * h, y - 0.18f * h);
                path.addRect(rect, Path.Direction.CCW);
                rect.set(x - 0.35f * h, y + 0.08f * h, x + 0.35f * h, y + 0.55f * h);
                path.addRect(rect, Path.Direction.CCW);
                path.setFillType(Path.FillType.EVEN_ODD);
                c.drawPath(path, inkFill);
                path.setFillType(Path.FillType.WINDING);
                break;
            }
            case IC_OK: {
                path.reset();
                path.moveTo(x - 0.45f * h, y + 0.02f * h);
                path.lineTo(x - 0.12f * h, y + 0.35f * h);
                path.lineTo(x + 0.5f * h, y - 0.35f * h);
                ink.setStrokeWidth(0.16f * h);
                c.drawPath(path, ink);
                break;
            }
            case IC_BACK: {
                path.reset();
                path.moveTo(x + 0.45f * h, y);
                path.lineTo(x - 0.4f * h, y);
                path.moveTo(x - 0.05f * h, y - 0.38f * h);
                path.lineTo(x - 0.45f * h, y);
                path.lineTo(x - 0.05f * h, y + 0.38f * h);
                ink.setStrokeWidth(0.14f * h);
                c.drawPath(path, ink);
                break;
            }
            default:
                break;
        }
    }

    private void drawPill(Canvas c, RectF r, String label, int color) {
        fill.setColor(color);
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, fill);
        text.setTextSize(14 * dp);
        text.setColor(0xFFFFFFFF);
        text.getTextBounds(label, 0, label.length(), textBounds);
        c.drawText(label, r.centerX(), r.centerY() - textBounds.exactCenterY(), text);
    }
}
