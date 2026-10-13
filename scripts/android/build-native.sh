#!/usr/bin/env bash
# Build libmain.so (+ libSDL2.so) for arm64-v8a outside Gradle, for fast
# compile iterations. Gradle (android/) runs the same CMake project.
#
#   ANDROID_NDK=<sdk>/ndk/<ver> GE_SDL2_SOURCE_DIR=<SDL2 2.x source> \
#     scripts/android/build-native.sh [build-dir]
set -euo pipefail
here="$(cd "$(dirname "$0")/../.." && pwd)"
: "${ANDROID_NDK:?set ANDROID_NDK to an NDK (r26+) directory}"
: "${GE_SDL2_SOURCE_DIR:?set GE_SDL2_SOURCE_DIR to an SDL2 2.x source tree}"
build="${1:-$here/build-android}"
cmake -S "$here" -B "$build" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
  -DCMAKE_BUILD_TYPE="${CMAKE_BUILD_TYPE:-RelWithDebInfo}" \
  -DROMID="${ROMID:-ntsc-final}" \
  -DGE_SDL2_SOURCE_DIR="$GE_SDL2_SOURCE_DIR"
cmake --build "$build" -j "${JOBS:-6}"
