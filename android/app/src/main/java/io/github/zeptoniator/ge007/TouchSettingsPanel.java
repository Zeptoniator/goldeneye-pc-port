package io.github.zeptoniator.ge007;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The Options panel (gear), shown over the running game inside the same
 * window (a dialog or another activity would take focus from SDL's
 * surface). "Opening Titles" (android/DESIGN.md, Titles): a rail of three
 * credits on the left, under the left thumb, with Resume at its foot; the
 * chosen section on the right as a credit title over its settings.
 * Choosing a section replays its title like an opening credit, the
 * tracking closing from wide to set. Every change is saved and applied at
 * once (touch settings in the "touch" preferences, game options through
 * GameOptions).
 */
public class TouchSettingsPanel extends FrameLayout {

    private static final int GAME = 0, TOUCH = 1, APP = 2;
    private static final float TITLE_TRACKING = 0.42f;
    private static final float TITLE_SP = 30f;
    /** The section shown when the panel opens again: the last one used. */
    private static int lastSection = GAME;

    private final TouchOverlay overlay;
    private final SharedPreferences prefs;
    private final TextView title;
    private final TextView subtitle;
    private final ScrollView scroll;
    private final LinearLayout[] sections = new LinearLayout[3];
    private final View[] creditRules = new View[3];
    private final TextView[] credits = new TextView[3];
    private final int[] titles = { R.string.opt_section_game, R.string.opt_section_touch, R.string.opt_section_app };
    private final int[] subtitles = { R.string.opt_game_sub, R.string.opt_touch_sub, R.string.opt_app_sub };
    private int current = -1;
    private final LinearLayout panel;
    private final View rule;
    private boolean confirmingReset;
    private LinearLayout rows;   // the section being filled by the add* helpers

    public TouchSettingsPanel(Context context, TouchOverlay overlay) {
        super(context);
        this.overlay = overlay;
        this.prefs = context.getSharedPreferences(TouchOverlay.PREFS, Context.MODE_PRIVATE);

        setBackgroundColor(Titles.SCRIM);
        setClickable(true);                 // swallow touches meant for the game
        setOnClickListener(v -> hide());    // tap the game around the panel: close

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Titles.BLACK);
        panel.setClickable(true);
        rule = new View(context);
        rule.setBackgroundColor(Titles.GOLD);
        panel.addView(rule, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, px(2)));

        LinearLayout split = new LinearLayout(context);
        split.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(split, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        // Rail: the three credits, Resume at the foot.
        LinearLayout rail = new LinearLayout(context);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setPadding(0, px(24), 0, px(16));
        for (int i = 0; i < 3; i++) {
            rail.addView(creditRow(i), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, px(48)));
        }
        rail.addView(new Space(context), new LinearLayout.LayoutParams(0, 0, 1f));
        Button resume = Titles.button(context, context.getString(R.string.opt_resume), true);
        resume.setOnClickListener(v -> hide());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, px(48));
        rlp.leftMargin = px(24);
        rlp.rightMargin = px(24);
        rail.addView(resume, rlp);
        split.addView(rail, new LinearLayout.LayoutParams(px(216), LayoutParams.MATCH_PARENT));

        View divider = new View(context);
        divider.setBackgroundColor(Titles.LINE);
        split.addView(divider, new LinearLayout.LayoutParams(px(1), LayoutParams.MATCH_PARENT));

        // Body: the credit title, its line, the section's settings.
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(px(32), px(24), px(32), 0);
        title = Titles.credit(context, TITLE_SP, TITLE_TRACKING, Titles.IVORY, false);
        title.setMinHeight(px(40));
        title.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(title, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        subtitle = Titles.label(context, 12, Titles.GREY, false);
        subtitle.setAllCaps(true);
        subtitle.setLetterSpacing(0.2f);
        subtitle.setMinHeight(px(24));
        body.addView(subtitle, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // More below or above: the list fades into the black at that end.
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(px(24));
        FrameLayout stack = new FrameLayout(context);
        stack.setPadding(0, px(8), 0, px(16));
        scroll.addView(stack);
        body.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        split.addView(body, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        for (int i = 0; i < 3; i++) {
            sections[i] = new LinearLayout(context);
            sections[i].setOrientation(LinearLayout.VERTICAL);
            sections[i].setVisibility(GONE);
            stack.addView(sections[i], new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        rows = sections[GAME];
        addSwitch(R.string.opt_invincible, () -> GameOptions.cheat(getContext(), GameOptions.CHEAT_INVINCIBLE),
                on -> GameOptions.setCheat(getContext(), GameOptions.CHEAT_INVINCIBLE, on), null);
        addSwitch(R.string.opt_infinite_ammo, () -> GameOptions.cheat(getContext(), GameOptions.CHEAT_INFINITE_AMMO),
                on -> GameOptions.setCheat(getContext(), GameOptions.CHEAT_INFINITE_AMMO, on), null);
        addSwitch(R.string.opt_all_guns, () -> GameOptions.cheat(getContext(), GameOptions.CHEAT_ALL_GUNS),
                on -> GameOptions.setCheat(getContext(), GameOptions.CHEAT_ALL_GUNS, on), null);
        addSwitch(R.string.opt_auto_aim, () -> GameOptions.nativeGetAutoAim() == 1,
                GameOptions::nativeSetAutoAim, () -> GameOptions.nativeGetAutoAim() >= 0);
        addSwitch(R.string.opt_all_unlocked, GameOptions::nativeGetAllUnlocked,
                GameOptions::nativeSetAllUnlocked, null);
        addNote(R.string.opt_difficulty_note);

        rows = sections[TOUCH];
        addSlider(R.string.touch_look, "lookSens", 25, 300, 100);
        addSlider(R.string.touch_look_y, "lookSensY", 25, 200, 100);
        addPrefSwitch(R.string.touch_invert_y, "invertY", false);
        addSlider(R.string.touch_opacity, "opacity", 10, 100, 100);
        addSlider(R.string.touch_size, "size", 70, 150, 100);
        addPrefSwitch(R.string.touch_haptics, "haptics", true);
        addPrefSwitch(R.string.touch_left_fire, "leftFire", false);
        addSwitch(R.string.touch_with_pads, () -> prefs.getBoolean("withPads", false), on -> {
            prefs.edit().putBoolean("withPads", on).apply();
            ((GoldenEyeActivity) getContext()).updateOverlayForControllers();
        }, null);
        addNote(R.string.touch_with_pads_note);
        addActions(
                Titles.button(context, context.getString(R.string.touch_move_buttons), false), v -> {
                    hide();
                    overlay.setEditMode(true);
                },
                Titles.button(context, context.getString(R.string.touch_defaults), false), this::defaultsTapped);

        rows = sections[APP];
        addValue(R.string.opt_rom_label, () -> context.getString(Regions.label(Regions.active(context))));
        addActions(
                Titles.button(context, context.getString(R.string.opt_change_rom), false), v -> {
                    hide();
                    ((GoldenEyeActivity) getContext()).changeRom();
                },
                Titles.button(context, context.getString(R.string.licences_title), false),
                v -> Licences.show((GoldenEyeActivity) getContext()));

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

    /** Touch-control keys "Defaults" resets; cheats share the file and stay. */
    private static final String[] TOUCH_KEYS = {
        "lookSens", "lookSensY", "invertY", "opacity", "size", "haptics", "withPads", "leftFire" };
    private final Runnable disarmReset = () -> armReset(null, false);

    /** "Defaults" takes two taps: the first turns it red for 3 s. */
    private void defaultsTapped(View v) {
        Button b = (Button) v;
        if (!confirmingReset) {
            armReset(b, true);
            return;
        }
        armReset(b, false);
        SharedPreferences.Editor e = prefs.edit();
        for (String k : TOUCH_KEYS) {
            e.remove(k);
        }
        e.apply();
        overlay.resetLayout();
        overlay.loadSettings();
        ((GoldenEyeActivity) getContext()).updateOverlayForControllers();
        rebuildValues();
    }

    private Button resetButton;

    private void armReset(Button b, boolean on) {
        if (b != null) {
            resetButton = b;
        }
        if (resetButton == null) {
            return;
        }
        confirmingReset = on;
        removeCallbacks(disarmReset);
        Context c = getContext();
        if (on) {
            resetButton.setText(R.string.touch_defaults_confirm);
            resetButton.setTextColor(Titles.IVORY);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setColor(Titles.RED);
            g.setStroke(px(1), Titles.RED);
            resetButton.setBackground(g);
            postDelayed(disarmReset, 3000);
        } else {
            Button fresh = Titles.button(c, c.getString(R.string.touch_defaults), false);
            resetButton.setText(R.string.touch_defaults);
            resetButton.setTextColor(fresh.getTextColors());
            resetButton.setBackground(fresh.getBackground());
        }
    }

    public boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    public void show() {
        overlay.releaseAll();
        rebuildValues();
        setVisibility(VISIBLE);
        bringToFront();
        current = -1;
        select(lastSection, true);
        Titles.openPanel(panel, rule);
    }

    public void hide() {
        armReset(null, false);
        setVisibility(GONE);
    }

    // ---------------------------------------------------------- sections

    private View creditRow(int i) {
        Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(24), 0, px(16), 0);
        row.setBackground(Titles.rowBackground());
        row.setFocusable(true);
        row.setOnClickListener(v -> select(i, true));
        creditRules[i] = new View(c);
        creditRules[i].setBackgroundColor(Titles.GOLD);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(px(16), px(2));
        rp.rightMargin = px(12);
        row.addView(creditRules[i], rp);
        credits[i] = Titles.credit(c, 11, 0.32f, Titles.GREY, false);
        credits[i].setText(titles[i]);
        row.addView(credits[i], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        credits[i].setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    /** Show section i; animate replays its title as an opening credit. */
    private void select(int i, boolean animate) {
        lastSection = i;
        for (int k = 0; k < 3; k++) {
            boolean on = k == i;
            creditRules[k].setVisibility(on ? VISIBLE : GONE);
            credits[k].setTextColor(on ? Titles.IVORY : Titles.GREY);
            sections[k].setVisibility(on ? VISIBLE : GONE);
        }
        if (i == current && animate) {
            return;
        }
        current = i;
        title.setText(titles[i]);
        subtitle.setText(subtitles[i]);
        scroll.scrollTo(0, 0);
        title.animate().cancel();
        fitTexts(animate);
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            sections[i].setAlpha(0f);
            sections[i].animate().alpha(1f).setDuration(160).start();
        } else {
            sections[i].setAlpha(1f);
        }
    }

    /**
     * Credit sizes, measured once laid out: the title as large as TITLE_SP
     * allows on its line, its credit replay starting no wider than fits;
     * the three rail credits at one shared size (the widest label against
     * the active row's room), so the active one never shrinks alone.
     */
    private void fitTexts(boolean animate) {
        int avail = title.getWidth() - title.getPaddingLeft() - title.getPaddingRight();
        View row = (View) credits[0].getParent();
        if (avail <= 0 || row.getWidth() <= 0) {
            if (isOpen()) {
                title.post(() -> fitTexts(animate));   // first layout still to come
            }
            return;
        }
        java.util.Locale loc = getResources().getConfiguration().getLocales().get(0);
        String t = title.getText().toString().toUpperCase(loc);
        title.setTextScaleX(1f);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Titles.fitCredit(title, t, TITLE_SP, TITLE_TRACKING, avail));
        if (animate) {
            Titles.creditIn(title, TITLE_TRACKING, avail);
        } else {
            title.setAlpha(1f);
            title.setLetterSpacing(TITLE_TRACKING);
        }
        int room = row.getWidth() - row.getPaddingLeft() - row.getPaddingRight() - px(28);
        float size = Float.MAX_VALUE;
        for (TextView c : credits) {
            String label = c.getText().toString().toUpperCase(loc);
            size = Math.min(size, Titles.fitCredit(c, label, 11, 0.32f, room));
        }
        for (TextView c : credits) {
            c.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        }
    }

    // ------------------------------------------------------------- rows

    private interface Refresh {
        void run();
    }

    private interface BoolGet {
        boolean get();
    }

    private interface BoolSet {
        void set(boolean v);
    }

    private interface TextGet {
        String get();
    }

    private final List<Refresh> refreshers = new ArrayList<>();
    private boolean refreshing;

    private void rebuildValues() {
        for (Refresh r : refreshers) {
            r.run();
        }
    }

    private void hairline() {
        View h = new View(getContext());
        h.setBackgroundColor(Titles.LINE);
        rows.addView(h, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, px(1)));
    }

    private String percent(int v) {
        return getContext().getString(R.string.opt_percent, v);
    }

    private void addSlider(int label, String key, int min, int max, int def) {
        Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout line = new LinearLayout(c);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Titles.label(c, 15, Titles.IVORY, false);
        name.setText(label);
        line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = Titles.label(c, 15, Titles.GOLD, true);
        value.setGravity(Gravity.END);
        line.addView(value, new LinearLayout.LayoutParams(px(64), ViewGroup.LayoutParams.WRAP_CONTENT));
        line.setMinimumHeight(px(32));
        row.addView(line, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        SeekBar bar = new SeekBar(c);
        Titles.styleSeekBar(c, bar);
        bar.setMax(max - min);
        bar.setContentDescription(c.getString(label));
        row.addView(bar, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, px(48)));
        Refresh r = () -> {
            int v = prefs.getInt(key, def);
            bar.setProgress(v - min);
            value.setText(percent(v));
        };
        r.run();
        refreshers.add(r);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                int v = p + min;
                value.setText(percent(v));
                prefs.edit().putInt(key, v).apply();
                overlay.loadSettings();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        rows.addView(row);
        hairline();
    }

    private void addPrefSwitch(int label, String key, boolean def) {
        addSwitch(label, () -> prefs.getBoolean(key, def), on -> {
            prefs.edit().putBoolean(key, on).apply();
            overlay.loadSettings();
        }, null);
    }

    /** A row with a switch bound to get/set; the whole row toggles it. {@code enabled} null = always. */
    private void addSwitch(int label, BoolGet get, BoolSet set, BoolGet enabled) {
        Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Titles.rowBackground());
        row.setFocusable(true);
        TextView name = Titles.label(c, 15, Titles.IVORY, false);
        name.setText(label);
        name.setPadding(0, px(12), px(16), px(12));
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Titles.SquareSwitch sw = new Titles.SquareSwitch(c);
        sw.setContentDescription(c.getString(label));
        row.addView(sw, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(24)));
        row.setOnClickListener(v -> {
            if (sw.isEnabled()) {
                sw.toggle();
            }
        });
        Refresh r = () -> {
            refreshing = true;
            sw.setChecked(get.get());
            boolean en = enabled == null || enabled.get();
            sw.setEnabled(en);
            row.setEnabled(en);
            row.setAlpha(en ? 1f : 0.4f);
            refreshing = false;
        };
        r.run();
        refreshers.add(r);
        sw.setOnCheckedChangeListener((b, on) -> {
            if (!refreshing) {
                set.set(on);
            }
        });
        row.setMinimumHeight(px(48));
        rows.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        hairline();
    }

    /** A read-only line: label on the left, its value on the right. */
    private void addValue(int label, TextGet get) {
        Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Titles.label(c, 15, Titles.IVORY, false);
        name.setText(label);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = Titles.label(c, 15, Titles.IVORY, true);
        row.addView(value);
        Refresh r = () -> value.setText(get.get());
        r.run();
        refreshers.add(r);
        rows.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, px(48)));
        hairline();
    }

    private void addNote(int label) {
        TextView n = Titles.label(getContext(), 12, Titles.GREY, false);
        n.setText(label);
        n.setLineSpacing(0, 1.15f);
        n.setPadding(0, px(8), 0, px(8));
        rows.addView(n);
    }

    private void addActions(Button a, View.OnClickListener la, Button b, View.OnClickListener lb) {
        LinearLayout line = new LinearLayout(getContext());
        line.setPadding(0, px(16), 0, 0);
        a.setOnClickListener(la);
        b.setOnClickListener(lb);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(48));
        p.rightMargin = px(8);
        line.addView(a, p);
        line.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(48)));
        rows.addView(line);
    }

}
