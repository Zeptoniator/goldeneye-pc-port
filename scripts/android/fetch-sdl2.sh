#!/usr/bin/env bash
# Download the SDL2 source release the Android build compiles against into
# android/third_party/SDL2 (git-ignored), the default location
# android/app/build.gradle looks in. SDL2 is zlib-licensed (its LICENSE.txt
# comes with the tree); the APK ships libSDL2.so and SDL's Java glue.
set -euo pipefail
here="$(cd "$(dirname "$0")/../.." && pwd)"
ver="${1:-2.32.10}"
dest="$here/android/third_party/SDL2"
[ -f "$dest/CMakeLists.txt" ] && { echo "already there: $dest"; exit 0; }
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
curl -fL "https://github.com/libsdl-org/SDL/releases/download/release-$ver/SDL2-$ver.tar.gz" -o "$tmp/sdl.tgz"
mkdir -p "$(dirname "$dest")"
tar -xzf "$tmp/sdl.tgz" -C "$tmp"
mv "$tmp/SDL2-$ver" "$dest"
echo "SDL2 $ver -> $dest"
