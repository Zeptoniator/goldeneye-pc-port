package io.github.zeptoniator.ge007;

import android.content.Context;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * The app panels' visual tokens, "Opening Titles" (android/DESIGN.md): a
 * spy film's opening credits. Near-black field, ivory text, gold only for
 * what is on, active or the primary action; Michroma for credit titles,
 * the system sans for everything you read; flat square controls on an
 * 8 dp grid, no gradient, glow or rounding.
 */
final class Titles {
    static final int BLACK = 0xFF07070A;
    static final int IVORY = 0xFFF2E6C9;
    static final int GREY = 0xFF8A8170;      // credits not selected, notes, off outlines
    static final int LINE = 0xFF2A2620;      // hairlines and empty slider track
    static final int GOLD = 0xFFD9A441;      // on, active, primary action
    static final int RED = 0xFF8C1C13;       // a destructive action awaiting confirmation
    static final int PRESSED = 0xFF16140F;   // touch feedback on rows and buttons
    static final int SCRIM = 0x9E07070A;     // over the running game

    private static Typeface credits;

    private Titles() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Michroma (OFL, assets/fonts): credit titles only. */
    static Typeface credits(Context c) {
        if (credits == null) {
            try {
                credits = Typeface.createFromAsset(c.getAssets(), "fonts/Michroma-Regular.ttf");
            } catch (RuntimeException e) {
                credits = Typeface.create("sans-serif", Typeface.NORMAL);
            }
        }
        return credits;
    }

    /**
     * A credit line: wide-tracked capitals. {@code shrink}: one line that
     * shrinks to fit its width (down to 7 sp) at any font scale; otherwise a
     * single line the caller sizes (the section title).
     */
    static TextView credit(Context c, float sp, float tracking, int color, boolean shrink) {
        TextView t = new TextView(c);
        t.setTypeface(credits(c));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setLetterSpacing(tracking);
        t.setTextColor(color);
        if (shrink) {
            // Not setSingleLine: auto-size does not run on a horizontally
            // scrolling line.
            t.setMaxLines(1);
            t.setAutoSizeTextTypeUniformWithConfiguration(7, Math.round(sp), 1, TypedValue.COMPLEX_UNIT_SP);
        } else {
            t.setSingleLine(true);  // before setAllCaps: it replaces the transformation
        }
        t.setAllCaps(true);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Body text in the system sans; tabular figures so values never jitter. */
    static TextView label(Context c, float sp, int color, boolean medium) {
        TextView t = new TextView(c);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setFontFeatureSettings("tnum");
        return t;
    }

    private static GradientDrawable rect(int fill, int strokeDp, int stroke, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(fill);
        if (strokeDp > 0) {
            g.setStroke(dp(c, strokeDp), stroke);
        }
        return g;
    }

    /**
     * The opening of a panel, as a title sequence opens: the panel fades up
     * from black and its gold rule draws in from the left (both 220 ms).
     * Instant when the system removes animations.
     */
    static void openPanel(android.view.View panel, android.view.View rule) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            panel.setAlpha(1f);
            rule.setScaleX(1f);
            return;
        }
        panel.setAlpha(0f);
        panel.animate().alpha(1f).setDuration(220).setInterpolator(new DecelerateInterpolator(2f)).start();
        rule.setPivotX(0f);
        rule.setScaleX(0f);
        rule.animate().scaleX(1f).setDuration(220).setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    /**
     * The signature move: a title fades in while its tracking closes from
     * +0.5 em to {@code to} (220 ms). A title that already fills its line
     * has no room to start wider, so while the tracking is open its glyphs
     * are condensed (text scale X, from a fresh measure each frame) to keep
     * the line within {@code avail} px: every title replays, none clips.
     */
    static void creditIn(TextView title, float to, int avail) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            title.setAlpha(1f);
            title.setLetterSpacing(to);
            title.setTextScaleX(1f);
            return;
        }
        // One replay per title at a time: a quick second tap cancels the first
        // (its end listener restores the set state before the new one starts).
        if (title.getTag() instanceof ValueAnimator) {
            ((ValueAnimator) title.getTag()).cancel();
        }
        String text = title.getText().toString()
                .toUpperCase(title.getResources().getConfiguration().getLocales().get(0));
        android.graphics.Paint p = new android.graphics.Paint(title.getPaint());
        p.setTextScaleX(1f);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(220);
        a.setInterpolator(new DecelerateInterpolator(2f));
        a.addUpdateListener(v -> {
            float f = (float) v.getAnimatedValue();
            float spacing = to + 0.5f * (1f - f);
            p.setLetterSpacing(spacing);
            float w = p.measureText(text);
            title.setAlpha(f);
            title.setLetterSpacing(spacing);
            title.setTextScaleX(w > avail ? avail / w : 1f);
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator an) {
                title.setLetterSpacing(to);
                title.setTextScaleX(1f);
            }
        });
        title.setTag(a);
        a.start();
    }

    /**
     * Size (px) for a credit on one line of {@code avail} px: as large as
     * {@code maxSp} allows at its set tracking.
     */
    static float fitCredit(TextView t, String text, float maxSp, float tracking, int avail) {
        float max = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, maxSp,
                t.getResources().getDisplayMetrics());
        android.graphics.Paint p = new android.graphics.Paint(t.getPaint());
        p.setTextScaleX(1f);
        p.setTextSize(max);
        p.setLetterSpacing(tracking);
        float w = p.measureText(text);
        return w > avail ? max * avail / w : max;
    }

    /** Press feedback for a whole row or a rail credit. */
    static Drawable rowBackground() {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable p = new GradientDrawable();
        p.setColor(PRESSED);
        s.addState(new int[] { android.R.attr.state_pressed }, p);
        GradientDrawable f = new GradientDrawable();
        f.setColor(PRESSED);
        s.addState(new int[] { android.R.attr.state_focused }, f);
        s.addState(new int[0], new GradientDrawable());
        return s;
    }

    /**
     * Square switch, drawn here rather than by styling android.widget.Switch
     * (Samsung's One UI draws the system switch itself and drops a custom
     * track): 44 x 24 dp, grey outline off, gold fill on, a 16 dp thumb that
     * slides over in 150 ms. A CompoundButton, so checked state, toggle and
     * the "switch" role for accessibility are the platform's.
     */
    static final class SquareSwitch extends CompoundButton {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float pos;          // 0 off .. 1 on
        private ValueAnimator anim;

        SquareSwitch(Context c) {
            super(c);
            setButtonDrawable((Drawable) null);
            setBackground(null);
            setText(null);
            setFocusable(true);
            setClickable(true);
        }

        @Override
        public void setChecked(boolean checked) {
            boolean was = isChecked();
            super.setChecked(checked);
            if (anim != null) {
                anim.cancel();
            }
            float to = checked ? 1f : 0f;
            if (was != checked && isLaidOut() && ValueAnimator.areAnimatorsEnabled()) {
                anim = ValueAnimator.ofFloat(pos, to);
                anim.setDuration(150);
                anim.setInterpolator(new DecelerateInterpolator(2f));
                anim.addUpdateListener(v -> {
                    pos = (float) v.getAnimatedValue();
                    invalidate();
                });
                anim.start();
            } else {
                pos = to;
                invalidate();
            }
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(resolveSize(dp(getContext(), 44), w), resolveSize(dp(getContext(), 24), h));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Context c = getContext();
            float s = dp(c, 1);
            float top = (getHeight() - dp(c, 24)) / 2f;
            r.set(0, top, dp(c, 44), top + dp(c, 24));
            if (pos > 0f) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(GOLD);
                paint.setAlpha(Math.round(255 * pos));
                canvas.drawRect(r, paint);
            }
            paint.setAlpha(255);
            if (pos < 1f) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s);
                paint.setColor(GREY);
                paint.setAlpha(Math.round(255 * (1f - pos)));
                r.inset(s / 2f, s / 2f);
                canvas.drawRect(r, paint);
                paint.setAlpha(255);
            }
            float inset = dp(c, 4), size = dp(c, 16);
            float x = inset + pos * (dp(c, 44) - size - 2 * inset);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pos > 0.5f ? BLACK : GREY);
            canvas.drawRect(x, top + inset, x + size, top + inset + size, paint);
            if (isFocused()) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s);
                paint.setColor(IVORY);
                canvas.drawRect(-s, top - s, dp(c, 44) + s, top + dp(c, 24) + s, paint);
            }
        }

        @Override
        public CharSequence getAccessibilityClassName() {
            return "android.widget.Switch";
        }
    }

    /** Flat slider: 2 dp track, gold up to the value, 16 dp ivory square thumb (gold held). */
    static void styleSeekBar(Context c, SeekBar sb) {
        GradientDrawable bg = rect(LINE, 0, 0, c);
        GradientDrawable fill = rect(GOLD, 0, 0, c);
        LayerDrawable track = new LayerDrawable(new Drawable[] {
            bg, new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL) });
        track.setId(0, android.R.id.background);
        track.setId(1, android.R.id.progress);
        for (int i = 0; i < 2; i++) {
            track.setLayerHeight(i, dp(c, 2));
            track.setLayerGravity(i, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL);
        }
        sb.setProgressDrawable(track);

        StateListDrawable thumb = new StateListDrawable();
        GradientDrawable held = rect(GOLD, 0, 0, c);
        held.setSize(dp(c, 16), dp(c, 16));
        GradientDrawable rest = rect(IVORY, 0, 0, c);
        rest.setSize(dp(c, 16), dp(c, 16));
        thumb.addState(new int[] { android.R.attr.state_pressed }, held);
        thumb.addState(new int[0], rest);
        sb.setThumb(thumb);
        sb.setThumbOffset(dp(c, 8));
        sb.setProgressTintList(null);
        sb.setProgressBackgroundTintList(null);
        sb.setThumbTintList(null);
        sb.setSplitTrack(false);
        sb.setBackground(null);
        sb.setPadding(dp(c, 8), 0, dp(c, 8), 0);
    }

    /**
     * A 48 dp button. Primary: gold outline and credit type, filled gold
     * when pressed. Secondary: grey outline, ivory label. Disabled: a dark
     * hairline and a grey label.
     */
    static Button button(Context c, CharSequence text, boolean primary) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(primary);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(c, 48));
        b.setMinimumHeight(dp(c, 48));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        int edge = primary ? GOLD : GREY;
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] { -android.R.attr.state_enabled }, rect(0x00000000, 1, LINE, c));
        bg.addState(new int[] { android.R.attr.state_pressed }, rect(primary ? GOLD : PRESSED, 1, edge, c));
        bg.addState(new int[] { android.R.attr.state_focused }, rect(PRESSED, 1, GOLD, c));
        bg.addState(new int[0], rect(0x00000000, 1, edge, c));
        b.setBackground(bg);
        int[][] states = { { -android.R.attr.state_enabled }, { android.R.attr.state_pressed }, {} };
        b.setMaxLines(1);
        if (primary) {
            b.setTypeface(credits(c));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            b.setLetterSpacing(0.28f);
            b.setAutoSizeTextTypeUniformWithConfiguration(7, 10, 1, TypedValue.COMPLEX_UNIT_SP);
            b.setTextColor(new ColorStateList(states, new int[] { GREY, BLACK, GOLD }));
        } else {
            b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            b.setTextColor(new ColorStateList(states, new int[] { GREY, IVORY, IVORY }));
        }
        return b;
    }
}
