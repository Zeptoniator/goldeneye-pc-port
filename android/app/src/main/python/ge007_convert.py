"""On-device asset preparation for the Android build.

Runs the PC port's own converters (tools_pc/d43_emit.py, d69_emit.py,
d88_emit.py, docs/building.md section 4), unchanged, against the ROM the
player picked. They read the ROM plus a few committed repo files
(scripts/filelist.*.csv, the file resource table, the model file headers),
shipped in the APK as ge007-convert-inputs.zip, and write
data/pcmodels-<region>/ and data/pccg-<region>/ relative to the working
directory -- exactly as on a PC.
"""
import contextlib
import io
import os
import runpy
import shutil
import sys
import zipfile

STEPS = (
    ("models", "d43_emit", []),
    ("stages", "d69_emit", []),
    ("setups", "d88_emit", ["--regen"]),
)


def convert(work_dir, inputs_zip, rom_path, out_data_dir, region, listener=None):
    """Convert into out_data_dir (the game's data/). Returns the log text.

    work_dir is scratch space (wiped first); rom_path must already be a
    verified big-endian .z64 for `region`. Raises RuntimeError on failure,
    with the converter's output tail in the message.
    """
    shutil.rmtree(work_dir, ignore_errors=True)
    os.makedirs(os.path.join(work_dir, "data"))
    with zipfile.ZipFile(inputs_zip) as z:
        z.extractall(work_dir)
    shutil.copyfile(rom_path, os.path.join(work_dir, "data", "ge007.%s.z64" % region))

    log = io.StringIO()
    old_cwd = os.getcwd()
    old_argv = sys.argv
    os.chdir(work_dir)
    try:
        for label, module, extra in STEPS:
            if listener is not None:
                listener.step(label)
            sys.argv = [module + ".py", region] + extra
            code = 0
            with contextlib.redirect_stdout(log), contextlib.redirect_stderr(log):
                try:
                    runpy.run_module(module, run_name="__main__", alter_sys=False)
                except SystemExit as e:
                    code = e.code if isinstance(e.code, int) else (0 if e.code is None else 1)
            if code != 0:
                raise RuntimeError("%s failed (exit %d):\n%s" % (module, code, log.getvalue()[-3000:]))
    finally:
        os.chdir(old_cwd)
        sys.argv = old_argv

    for sub in ("pcmodels-" + region, "pccg-" + region):
        src = os.path.join(work_dir, "data", sub)
        dst = os.path.join(out_data_dir, sub)
        if not os.path.isfile(os.path.join(src, "manifest.csv")):
            raise RuntimeError("%s was not produced:\n%s" % (sub, log.getvalue()[-3000:]))
        shutil.rmtree(dst, ignore_errors=True)
        # Plain data copies: shared storage (FUSE) may refuse chmod/utime,
        # which shutil.copytree's copystat would turn into an error.
        os.makedirs(dst, exist_ok=True)
        for name in os.listdir(src):
            shutil.copyfile(os.path.join(src, name), os.path.join(dst, name))
    shutil.rmtree(work_dir, ignore_errors=True)
    return log.getvalue()
