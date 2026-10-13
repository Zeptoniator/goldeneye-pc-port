package io.github.zeptoniator.ge007;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The licences of everything in the APK: assets/licences.txt, put together
 * at build time (app/build.gradle, ge007Licences) from the repo's LICENSE
 * and NOTICE, SDL2's licence and android/licences/.
 */
final class Licences {
    private Licences() {
    }

    static void show(Activity a) {
        String text;
        try (InputStream in = a.getAssets().open("licences.txt")) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[1 << 14];
            int n;
            while ((n = in.read(b)) > 0) {
                buf.write(b, 0, n);
            }
            text = buf.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            text = e.toString();
        }
        float dp = a.getResources().getDisplayMetrics().density;
        TextView tv = new TextView(a);
        tv.setText(text);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTextIsSelectable(true);
        int pad = Math.round(16 * dp);
        tv.setPadding(pad, pad, pad, pad);
        // The texts are hard-wrapped at 72-80 columns: keep their lines and
        // scroll sideways on a narrow screen rather than wrap them again.
        tv.setHorizontallyScrolling(true);
        HorizontalScrollView wide = new HorizontalScrollView(a);
        wide.addView(tv);
        ScrollView scroll = new ScrollView(a);
        scroll.addView(wide);
        new AlertDialog.Builder(a)
                .setTitle(R.string.licences_title)
                .setView(scroll)
                .setPositiveButton(R.string.licences_close, null)
                .show();
    }
}
