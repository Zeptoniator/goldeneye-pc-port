package io.github.zeptoniator.ge007;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Save / load panel over the game (same window as SDL's surface, like
 * TouchSettingsPanel): the automatic slot (load only) and three slots.
 * Saving works during a solo mission; loading works anywhere.
 */
public class SaveStatesPanel extends FrameLayout {

    private final Activity activity;
    private final TouchOverlay overlay;
    private final float dp;
    private final LinearLayout rows;
    private final TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());

    public SaveStatesPanel(Activity activity, TouchOverlay overlay) {
        super(activity);
        this.activity = activity;
        this.overlay = overlay;
        this.dp = activity.getResources().getDisplayMetrics().density;

        setBackgroundColor(0x99000000);
        setClickable(true);
        setOnClickListener(v -> hide());

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(px(20), px(14), px(20), px(14));
        card.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0202124);
        bg.setCornerRadius(px(16));
        card.setBackground(bg);

        TextView title = new TextView(activity);
        title.setText(R.string.ss_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        card.addView(title);

        rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        card.addView(rows);

        status = new TextView(activity);
        status.setTextColor(0xFF9E9E9E);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setPadding(0, px(6), 0, 0);
        card.addView(status);

        Button close = new Button(activity);
        close.setText(R.string.touch_close);
        close.setAllCaps(false);
        close.setOnClickListener(v -> hide());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.END;
        card.addView(close, clp);

        LayoutParams lp = new LayoutParams(px(520), LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(card, lp);
        setVisibility(GONE);
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    public boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    public void show() {
        overlay.releaseAll();
        status.setText(SaveStates.nativeCanSave() ? "" : activity.getString(R.string.ss_cannot_save));
        rebuild();
        setVisibility(VISIBLE);
        bringToFront();
    }

    public void hide() {
        setVisibility(GONE);
    }

    private void rebuild() {
        rows.removeAllViews();
        boolean canSave = SaveStates.nativeCanSave();
        for (int slot = 0; slot < SaveStates.SLOTS; slot++) {
            final int s = slot;
            long[] info = SaveStates.nativePeek(SaveStates.file(activity, s).getPath());

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, px(6), 0, px(6));

            LinearLayout text = new LinearLayout(activity);
            text.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(activity);
            name.setText(s == SaveStates.AUTO ? activity.getString(R.string.ss_auto)
                    : activity.getString(R.string.ss_slot, s));
            name.setTextColor(0xFFE0E0E0);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            TextView what = new TextView(activity);
            what.setText(SaveStates.describe(activity, info));
            what.setTextColor(0xFF9E9E9E);
            what.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            text.addView(name);
            text.addView(what);
            row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            if (s != SaveStates.AUTO) {
                Button save = new Button(activity);
                save.setText(R.string.ss_save);
                save.setAllCaps(false);
                save.setEnabled(canSave);
                save.setOnClickListener(v -> doSave(s));
                row.addView(save);
            }
            Button load = new Button(activity);
            load.setText(R.string.ss_load);
            load.setAllCaps(false);
            load.setEnabled(info != null && info[0] == 1);
            load.setOnClickListener(v -> doLoad(s));
            row.addView(load);
            rows.addView(row);
        }
    }

    private void doSave(int slot) {
        status.setText(R.string.ss_saving);
        new Thread(() -> {
            boolean ok = SaveStates.saveAndWait(activity, slot, 5000);
            String msg = ok ? activity.getString(R.string.ss_saved) : SaveStates.nativeLastMessage();
            ui.post(() -> {
                status.setText(msg);
                rebuild();
            });
        }, "ge007-savestate-ui").start();
    }

    private void doLoad(int slot) {
        SaveStates.nativeLoad(SaveStates.file(activity, slot).getPath());
        hide();
        // Report a failure (another build, damaged file) once the game tried.
        new Thread(() -> {
            long end = android.os.SystemClock.uptimeMillis() + 30000;
            while (SaveStates.nativeBusy() && android.os.SystemClock.uptimeMillis() < end) {
                android.os.SystemClock.sleep(50);
            }
            int r = SaveStates.nativeLastResult();
            if (r < 0) {
                String msg = SaveStates.nativeLastMessage();
                ui.post(() -> android.widget.Toast.makeText(activity, msg,
                        android.widget.Toast.LENGTH_LONG).show());
            }
        }, "ge007-savestate-ui").start();
    }
}
