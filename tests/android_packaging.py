#!/usr/bin/env python3
"""Verify the native tools survive a Gradle clean with the CMake cache retained.

Run from any directory with the Android build prerequisites on PATH:
    python3 tests/android_packaging.py
"""

from pathlib import Path
import subprocess
import zipfile


ANDROID = Path(__file__).resolve().parents[1] / "android"
APK = ANDROID / "app/build/outputs/apk/debug/app-debug.apk"
REQUIRED = {
    f"lib/{abi}/{binary}"
    for abi in ("arm64-v8a", "x86_64")
    for binary in ("libtinc.so", "libtincd.so")
}


def build(*tasks):
    subprocess.run([str(ANDROID / "gradlew"), *tasks, "--console=plain"], cwd=ANDROID, check=True)
    with zipfile.ZipFile(APK) as package:
        missing = REQUIRED - set(package.namelist())
        if missing:
            raise RuntimeError(f"APK is missing native tools: {', '.join(sorted(missing))}")
    print(f"Native tools present for both ABIs after {' '.join(tasks)}", flush=True)


if __name__ == "__main__":
    build(":app:assembleDebug")
    # :app:clean removes AGP's staged libraries, but deliberately retains .cxx.
    build(":app:clean", ":app:assembleDebug")
