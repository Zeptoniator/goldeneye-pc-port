package io.github.zeptoniator.ge007;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Launcher: makes sure the game's data/ directory holds the ROM and the PC
 * asset sidecars, then starts the game.
 *
 * The APK ships no game data. On first launch the player picks their own
 * GoldenEye 007 ROM (US, Europe or Japan, see Regions); it is checked (byte
 * order, SHA-1 gives the region), copied to data/, and converted on the
 * device by the PC port's own converters (Python, see
 * src/main/python/ge007_convert.py). Later launches go straight to the game
 * of the active region, unless an APK update changed the converters
 * (data/.android-convert-stamp[-region]). EXTRA_CHANGE_ROM (the options
 * panel) shows the screen anyway: pick another ROM, or switch to a region
 * imported before.
 */
public class SetupActivity extends Activity {

    static final String EXTRA_CHANGE_ROM = "changeRom";
    private static final int PICK_ROM = 1;
    private static final String STAMP = ".android-convert-stamp";

    private TextView status;
    private TextView details;
    private Button pick;
    private ProgressBar progress;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean busy;

    static File dataDir(Activity a) {
        File base = a.getExternalFilesDir(null);
        if (base == null) {
            base = a.getFilesDir();
        }
        return new File(base, "data");
    }

    static File romFile(Activity a, String region) {
        return new File(dataDir(a), "ge007." + region + ".z64");
    }

    private static boolean sidecarsPresent(File data, String region) {
        String[] needed = {
            "pcmodels-" + region + "/pcmodels.bin", "pcmodels-" + region + "/manifest.csv",
            "pccg-" + region + "/pccg.bin", "pccg-" + region + "/manifest.csv",
        };
        for (String n : needed) {
            if (!new File(data, n).isFile()) {
                return false;
            }
        }
        return true;
    }

    private static File stampFile(File data, String region) {
        return new File(data, Regions.NTSC.equals(region) ? STAMP : STAMP + "-" + region);
    }

    private String stampValue(String region) {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName + "/" + pi.getLongVersionCode() + "/" + region;
        } catch (Exception e) {
            return region;
        }
    }

    private boolean stampCurrent(File data, String region) {
        File f = stampFile(data, region);
        if (!f.isFile()) {
            // Data pushed by scripts/android/push-data.sh: trust it.
            return true;
        }
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[256];
            int n = in.read(b);
            return n > 0 && new String(b, 0, n, StandardCharsets.UTF_8).trim().equals(stampValue(region));
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean regionReady(Activity a, String region) {
        return romFile(a, region).isFile() && sidecarsPresent(dataDir(a), region);
    }

    /** True when the game of the active region can start right away. */
    static boolean dataReady(Activity a) {
        return regionReady(a, Regions.active(a));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        File data = dataDir(this);
        //noinspection ResultOfMethodCallIgnored
        data.mkdirs();
        String region = Regions.active(this);
        boolean change = getIntent().getBooleanExtra(EXTRA_CHANGE_ROM, false);

        if (!change && regionReady(this, region) && stampCurrent(data, region)) {
            startGame();
            return;
        }
        buildUi();
        if (!change && romFile(this, region).isFile()) {
            // ROM already there (earlier import, or an APK update changed the
            // converters): just convert again.
            runInBackground(() -> convertOnly(region));
        }
    }

    // ---------------------------------------------------------------- UI

    private int px(float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics()));
    }

    private void buildUi() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(px(32), px(24), px(32), px(24));
        col.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        col.addView(title);

        status = new TextView(this);
        status.setText(R.string.setup_intro);
        status.setTextColor(0xFFDDDDDD);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, px(16), 0, px(16));
        col.addView(status);

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        col.addView(progress);

        pick = new Button(this);
        pick.setText(R.string.setup_pick);
        pick.setOnClickListener(v -> pickRom());
        col.addView(pick);

        // Regions imported before: switch without importing again.
        for (String r : Regions.ALL) {
            if (Regions.built(r) && regionReady(this, r)) {
                Button play = new Button(this);
                play.setText(getString(R.string.setup_play_region, getString(Regions.label(r))));
                play.setOnClickListener(v -> {
                    if (busy) {
                        return;
                    }
                    if (stampCurrent(dataDir(this), r)) {
                        Regions.setActive(this, r);
                        startGame();
                    } else {
                        runInBackground(() -> convertOnly(r));
                    }
                });
                col.addView(play);
            }
        }

        Button licences = new Button(this, null, android.R.attr.borderlessButtonStyle);
        licences.setText(R.string.licences_title);
        licences.setTextColor(0xFF999999);
        licences.setOnClickListener(v -> Licences.show(this));
        col.addView(licences);

        details = new TextView(this);
        details.setTextColor(0xFF999999);
        details.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        details.setTypeface(Typeface.MONOSPACE);
        details.setPadding(0, px(16), 0, 0);
        col.addView(details);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(col);
        setContentView(scroll);
    }

    private void setBusy(boolean on, int message) {
        ui.post(() -> {
            busy = on;
            status.setText(message);
            progress.setVisibility(on ? View.VISIBLE : View.GONE);
            pick.setEnabled(!on);
            if (on) {
                details.setText("");
            }
        });
    }

    private void fail(int message, String detail) {
        ui.post(() -> {
            busy = false;
            status.setText(message);
            progress.setVisibility(View.GONE);
            pick.setEnabled(true);
            details.setText(detail == null ? "" : detail);
        });
    }

    private void pickRom() {
        if (busy) {
            return;
        }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, PICK_ROM);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_ROM && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            runInBackground(() -> importRom(uri));
        }
    }

    private void runInBackground(Runnable r) {
        Thread t = new Thread(r, "ge007-setup");
        t.start();
    }

    private void startGame() {
        startActivity(new Intent(this, GoldenEyeActivity.class));
        finish();
    }

    // ------------------------------------------------------------ import

    /** N64 ROMs come in three byte orders; the port wants big-endian .z64. */
    private static void toZ64(byte[] rom) throws IOException {
        int m = ((rom[0] & 0xFF) << 24) | ((rom[1] & 0xFF) << 16) | ((rom[2] & 0xFF) << 8) | (rom[3] & 0xFF);
        if (m == 0x80371240) {
            return;                                 // .z64
        }
        if (m == 0x37804012) {                      // .v64: 16-bit byte-swapped
            for (int i = 0; i + 1 < rom.length; i += 2) {
                byte t = rom[i]; rom[i] = rom[i + 1]; rom[i + 1] = t;
            }
            return;
        }
        if (m == 0x40123780) {                      // .n64: 32-bit little-endian words
            for (int i = 0; i + 3 < rom.length; i += 4) {
                byte a = rom[i], b = rom[i + 1];
                rom[i] = rom[i + 3]; rom[i + 1] = rom[i + 2]; rom[i + 2] = b; rom[i + 3] = a;
            }
            return;
        }
        throw new IOException("not an N64 ROM");
    }

    private static String sha1(byte[] b) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : d) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }

    private void importRom(Uri uri) {
        setBusy(true, R.string.setup_checking);
        byte[] rom;
        String region;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("cannot open");
            }
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream(12 << 20);
            byte[] chunk = new byte[1 << 16];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
                if (buf.size() > (64 << 20)) {
                    throw new IOException("file too large");
                }
            }
            rom = buf.toByteArray();
        } catch (IOException e) {
            fail(R.string.setup_read_failed, e.getMessage());
            return;
        }
        try {
            if (rom.length < 0x1000) {
                throw new IOException("file too small");
            }
            toZ64(rom);
            String h = sha1(rom);
            region = Regions.forSha1(h);
            if (region == null) {
                String country = rom.length > 0x3E ? String.valueOf((char) (rom[0x3E] & 0xFF)) : "?";
                StringBuilder exp = new StringBuilder();
                for (String r : Regions.ALL) {
                    exp.append("\n").append(Regions.sha1(r)).append("  ").append(getString(Regions.label(r)));
                }
                fail(R.string.setup_wrong_rom, "SHA-1 " + h + "\ncountry code: " + country
                        + "\n\nexpected one of:" + exp);
                return;
            }
            if (!Regions.built(region)) {
                fail(R.string.setup_region_missing, getString(Regions.label(region)));
                return;
            }
        } catch (Exception e) {
            fail(R.string.setup_not_rom, e.getMessage());
            return;
        }
        File dst = romFile(this, region);
        File tmp = new File(dst.getPath() + ".part");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(rom);
        } catch (IOException e) {
            fail(R.string.setup_write_failed, e.getMessage());
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        dst.delete();
        if (!tmp.renameTo(dst)) {
            fail(R.string.setup_write_failed, tmp.getPath());
            return;
        }
        convertOnly(region);
    }

    /** Progress callback for the Python side (ge007_convert.convert). */
    public final class Progress {
        public void step(String name) {
            int msg = "models".equals(name) ? R.string.setup_step_models
                    : "stages".equals(name) ? R.string.setup_step_stages
                    : R.string.setup_step_setups;
            setBusy(true, msg);
        }
    }

    /** Convert the region's ROM, make it the active region, start the game. */
    private void convertOnly(String region) {
        setBusy(true, R.string.setup_preparing);
        File data = dataDir(this);
        File work = new File(getCacheDir(), "convert");
        File zip = new File(getCacheDir(), "ge007-convert-inputs.zip");
        try (InputStream in = getAssets().open("ge007-convert-inputs.zip");
             OutputStream out = new FileOutputStream(zip)) {
            byte[] b = new byte[1 << 16];
            int n;
            while ((n = in.read(b)) > 0) {
                out.write(b, 0, n);
            }
        } catch (IOException e) {
            fail(R.string.setup_convert_failed, e.toString());
            return;
        }
        try {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(this));
            }
            PyObject mod = Python.getInstance().getModule("ge007_convert");
            mod.callAttr("convert", work.getPath(), zip.getPath(), romFile(this, region).getPath(),
                    data.getPath(), region, new Progress());
        } catch (Throwable t) {
            fail(R.string.setup_convert_failed, t.getMessage());
            return;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            zip.delete();
        }
        try (OutputStream out = new FileOutputStream(stampFile(data, region))) {
            out.write(stampValue(region).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // Without the stamp the data is still used (see stampCurrent).
        }
        Regions.setActive(this, region);
        ui.post(this::startGame);
    }
}
