#!/usr/bin/env bash
# Copy the ROM and the PC asset sidecars to the Android app over adb.
# (The app can do this by itself: on first launch it asks for the ROM and
# converts it on the device. This script is the developer shortcut.)
#
#   scripts/android/push-data.sh <rom.z64> [romid] [adb serial]
#
# romid: ntsc-final (default), pal-final, jpn-final -- must match the APK's
# build (-Pge.romId). The sidecars are generated here (pure-Python
# converters, docs/building.md section 4) into a scratch data dir, then
# pushed to the app's external files dir, which the game uses as data/.
# Never commit or redistribute the ROM or the generated sidecars.
set -euo pipefail
here="$(cd "$(dirname "$0")/../.." && pwd)"
rom="${1:?usage: push-data.sh <rom.z64> [romid] [adb serial]}"
romid="${2:-ntsc-final}"
serial="${3:-${ANDROID_SERIAL:-}}"
pkg="io.github.zeptoniator.ge007"
adb=(adb); [ -n "$serial" ] && adb=(adb -s "$serial")

# The converters read repo files relative to the repo root and write
# <root>/data/... (git-ignored), exactly as for the PC build.
mkdir -p "$here/data"
cp "$rom" "$here/data/ge007.$romid.z64"
( cd "$here" && python3 tools_pc/d43_emit.py "$romid" \
             && python3 tools_pc/d69_emit.py "$romid" \
             && python3 tools_pc/d88_emit.py "$romid" --regen ) >/dev/null

dest="/sdcard/Android/data/$pkg/files/data"
# Let the app create its own directories first (a directory made by adb in
# Android/data belongs to the shell and the app cannot enter it).
"${adb[@]}" shell am start -W -n "$pkg/.GoldenEyeActivity" >/dev/null
sleep 3
"${adb[@]}" shell am force-stop "$pkg"
"${adb[@]}" shell "[ -d '$dest' ]" || { echo "app did not create $dest" >&2; exit 1; }

"${adb[@]}" push "$here/data/ge007.$romid.z64" "$dest/" >/dev/null
for d in "pcmodels-$romid" "pccg-$romid"; do
  "${adb[@]}" push "$here/data/$d" "$dest/" >/dev/null
  # adb creates these 2770 shell:ext_data_rw; open them to the app.
  "${adb[@]}" shell chmod 2775 "$dest/$d"
done
"${adb[@]}" shell ls -lR "$dest" | head -20
echo "pushed $romid data to $dest"
