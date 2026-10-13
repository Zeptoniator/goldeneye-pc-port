package io.github.zeptoniator.ge007;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Touch-control settings, shown over the game inside the same window (a
 * dialog or another activity would take focus from SDL's surface).
 * Every change is saved to the "touch" preferences and applied at once.
 */
public class TouchSettingsPanel extends FrameLayout {

    private final TouchOverlay overlay;
    private final SharedPreferences prefs;
    private final float dp;
    private final LinearLayout rows;

    public TouchSettingsPanel(Context context, TouchOverlay overlay) {
        super(context);
        this.overlay = overlay;
        this.prefs = context.getSharedPreferences(TouchOverlay.PREFS, Context.MODE_PRIVATE);
        this.dp = context.getResources().getDisplayMetrics().density;

        setBackgroundColor(0x99000000);
        setClickable(true);                 // swallow touches meant for the game
        setOnClickListener(v -> hide());    // tap outside the card: close

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(px(20), px(14), px(20), px(14));
        card.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0202124);
        bg.setCornerRadius(px(16));
        card.setBackground(bg);

        TextView title = new TextView(context);
        title.setText(R.string.opt_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setPadding(0, 0, 0, px(6));
        card.addView(title);

        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(rows);
        card.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        addHeader(R.string.opt_game);
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

        addHeader(R.string.touch_settings_title);
        addSlider(R.string.touch_look, "lookSens", 25, 300, 100);
        addSlider(R.string.touch_look_y, "lookSensY", 25, 200, 100);
        addPrefSwitch(R.string.touch_invert_y, "invertY", false);
        addSlider(R.string.touch_opacity, "opacity", 10, 100, 100);
        addSlider(R.string.touch_size, "size", 70, 150, 100);
        addPrefSwitch(R.string.touch_haptics, "haptics", true);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, px(8), 0, 0);
        actions.addView(action(R.string.touch_defaults, v -> {
            prefs.edit().clear().apply();
            overlay.resetLayout();
            overlay.loadSettings();
            GameOptions.applySaved(getContext());   // cheats back off
            rebuildValues();
        }));
        actions.addView(action(R.string.touch_move_buttons, v -> {
            hide();
            overlay.setEditMode(true);
        }));
        actions.addView(action(R.string.touch_close, v -> hide()));
        card.addView(actions);

        LayoutParams lp = new LayoutParams(px(460), LayoutParams.MATCH_PARENT, Gravity.CENTER);
        lp.topMargin = px(16);
        lp.bottomMargin = px(16);
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
        rebuildValues();
        setVisibility(VISIBLE);
        bringToFront();
    }

    public void hide() {
        setVisibility(GONE);
    }

    // ------------------------------------------------------------- rows

    private interface Refresh {
        void run();
    }

    private final java.util.List<Refresh> refreshers = new java.util.ArrayList<>();

    private void rebuildValues() {
        for (Refresh r : refreshers) {
            r.run();
        }
    }

    private void addSlider(int label, String key, int min, int max, int def) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, px(6), 0, px(2));
        TextView name = new TextView(getContext());
        name.setTextColor(0xFFE0E0E0);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        row.addView(name);
        SeekBar bar = new SeekBar(getContext());
        bar.setMax(max - min);
        row.addView(bar);
        String text = getContext().getString(label);
        Refresh r = () -> {
            int v = prefs.getInt(key, def);
            bar.setProgress(v - min);
            name.setText(text + " : " + v + " %");
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
                name.setText(text + " : " + v + " %");
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
    }

    private interface BoolGet {
        boolean get();
    }

    private interface BoolSet {
        void set(boolean v);
    }

    private boolean refreshing;

    private void addPrefSwitch(int label, String key, boolean def) {
        addSwitch(label, () -> prefs.getBoolean(key, def), on -> {
            prefs.edit().putBoolean(key, on).apply();
            overlay.loadSettings();
        }, null);
    }

    /** A switch bound to get/set; {@code enabled} null = always enabled. */
    private void addSwitch(int label, BoolGet get, BoolSet set, BoolGet enabled) {
        Switch sw = new Switch(getContext());
        sw.setText(label);
        sw.setTextColor(0xFFE0E0E0);
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sw.setPadding(0, px(8), 0, px(8));
        Refresh r = () -> {
            refreshing = true;
            sw.setChecked(get.get());
            sw.setEnabled(enabled == null || enabled.get());
            refreshing = false;
        };
        r.run();
        refreshers.add(r);
        sw.setOnCheckedChangeListener((b, on) -> {
            if (!refreshing) {
                set.set(on);
            }
        });
        rows.addView(sw);
    }

    private void addHeader(int label) {
        TextView h = new TextView(getContext());
        h.setText(label);
        h.setTextColor(0xFF8AB4F8);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        h.setAllCaps(true);
        h.setPadding(0, px(12), 0, px(2));
        rows.addView(h);
    }

    private void addNote(int label) {
        TextView n = new TextView(getContext());
        n.setText(label);
        n.setTextColor(0xFF9E9E9E);
        n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        n.setPadding(0, px(2), 0, px(6));
        rows.addView(n);
    }

    private Button action(int label, View.OnClickListener l) {
        Button b = new Button(getContext());
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }
}
