package io.github.zeptoniator.ge007;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

/**
 * Save / load panel over the game (same window as SDL's surface, like
 * TouchSettingsPanel, and the same "Opening Titles" look, Titles): the
 * rail says what saving and loading can do right now and holds Resume
 * under the left thumb; the right side lists the automatic slot (load
 * only) and three slots. Saving works during a solo mission; loading
 * works anywhere.
 */
public class SaveStatesPanel extends FrameLayout {

    private final Activity activity;
    private final TouchOverlay overlay;
    private final LinearLayout rows;
    private final TextView status;
    private final TextView title;
    private final LinearLayout panel;
    private final View rule;
    private final ScrollView scroll;
    private final Handler ui = new Handler(Looper.getMainLooper());

    public SaveStatesPanel(Activity activity, TouchOverlay overlay) {
        super(activity);
        this.activity = activity;
        this.overlay = overlay;

        setBackgroundColor(Titles.SCRIM);
        setClickable(true);                 // swallow touches meant for the game
        setOnClickListener(v -> hide());    // tap the game around the panel: close

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Titles.BLACK);
        panel.setClickable(true);
        rule = new View(activity);
        rule.setBackgroundColor(Titles.GOLD);
        panel.addView(rule, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, px(2)));

        LinearLayout split = new LinearLayout(activity);
        split.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(split, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        // Rail: what saving and loading can do now, Resume at the foot.
        LinearLayout rail = new LinearLayout(activity);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setPadding(px(24), px(32), px(24), px(16));
        status = Titles.label(activity, 13, Titles.GREY, false);
        status.setLineSpacing(0, 1.15f);
        rail.addView(status);
        rail.addView(new Space(activity), new LinearLayout.LayoutParams(0, 0, 1f));
        Button resume = Titles.button(activity, activity.getString(R.string.opt_resume), true);
        resume.setOnClickListener(v -> hide());
        rail.addView(resume, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, px(48)));
        split.addView(rail, new LinearLayout.LayoutParams(px(216), LayoutParams.MATCH_PARENT));

        View divider = new View(activity);
        divider.setBackgroundColor(Titles.LINE);
        split.addView(divider, new LinearLayout.LayoutParams(px(1), LayoutParams.MATCH_PARENT));

        // Body: the credit title, its line, the slots.
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(px(32), px(24), px(32), 0);
        title = Titles.credit(activity, 30, 0.42f, Titles.IVORY, false);
        title.setText(R.string.ss_title);
        title.setMinHeight(px(40));
        title.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(title, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView subtitle = Titles.label(activity, 12, Titles.GREY, false);
        subtitle.setText(R.string.ss_sub);
        subtitle.setAllCaps(true);
        subtitle.setLetterSpacing(0.2f);
        subtitle.setMinHeight(px(24));
        body.addView(subtitle, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(px(24));
        rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, px(8), 0, px(16));
        scroll.addView(rows);
        body.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        split.addView(body, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        lp.leftMargin = px(88);
        lp.rightMargin = px(88);
        lp.topMargin = px(16);
        lp.bottomMargin = px(16);
        addView(panel, lp);
        setVisibility(GONE);
    }

    private int px(float v) {
        return Titles.dp(getContext(), v);
    }

    public boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    public void show() {
        overlay.releaseAll();
        rebuild();
        status.setText(stateNote());
        setVisibility(VISIBLE);
        bringToFront();
        scroll.scrollTo(0, 0);
        Titles.openPanel(panel, rule);
        fitTitle(true);
    }

    private boolean anyLoadable;

    /** What the panel can do right now, in the player's words. */
    private String stateNote() {
        if (SaveStates.nativeCanSave()) {
            return activity.getString(R.string.ss_can_save);
        }
        return activity.getString(anyLoadable ? R.string.ss_cannot_save : R.string.ss_cannot_save_only);
    }

    /** The title as large as 30 sp allows on its line; replays as a credit on opening. */
    private void fitTitle(boolean animate) {
        int avail = title.getWidth() - title.getPaddingLeft() - title.getPaddingRight();
        if (avail <= 0) {
            if (isOpen()) {
                title.post(() -> fitTitle(animate));
            }
            return;
        }
        String t = title.getText().toString().toUpperCase(getResources().getConfiguration().getLocales().get(0));
        title.setTextScaleX(1f);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Titles.fitCredit(title, t, 30, 0.42f, avail));
        if (animate) {
            Titles.creditIn(title, 0.42f, avail);
        }
    }

    public void hide() {
        setVisibility(GONE);
    }

    private void rebuild() {
        rows.removeAllViews();
        boolean canSave = SaveStates.nativeCanSave();
        anyLoadable = false;
        for (int slot = 0; slot < SaveStates.SLOTS; slot++) {
            final int s = slot;
            long[] info = SaveStates.nativePeek(SaveStates.file(activity, s).getPath());

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, px(8), 0, px(8));
            row.setMinimumHeight(px(64));

            LinearLayout text = new LinearLayout(activity);
            text.setOrientation(LinearLayout.VERTICAL);
            TextView name = Titles.label(activity, 15, Titles.IVORY, false);
            name.setText(s == SaveStates.AUTO ? activity.getString(R.string.ss_auto)
                    : activity.getString(R.string.ss_slot, s));
            TextView what = Titles.label(activity, 12, Titles.GREY, false);
            what.setText(SaveStates.describe(activity, info));
            what.setPadding(0, px(2), 0, 0);
            text.addView(name);
            text.addView(what);
            text.setPadding(0, 0, px(16), 0);
            row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (s != SaveStates.AUTO) {
                Button save = Titles.button(activity, activity.getString(R.string.ss_save), false);
                save.setEnabled(canSave);
                save.setOnClickListener(v -> doSave(s));
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(48));
                sp.rightMargin = px(8);
                row.addView(save, sp);
            }
            Button load = Titles.button(activity, activity.getString(R.string.ss_load), false);
            load.setEnabled(info != null && info[0] == 1);
            anyLoadable |= load.isEnabled();
            load.setOnClickListener(v -> doLoad(s));
            row.addView(load, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(48)));
            rows.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View h = new View(activity);
            h.setBackgroundColor(Titles.LINE);
            rows.addView(h, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, px(1)));
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
            long end = SystemClock.uptimeMillis() + 30000;
            while (SaveStates.nativeBusy() && SystemClock.uptimeMillis() < end) {
                SystemClock.sleep(50);
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
