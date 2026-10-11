# Tincer for Android

Tincer is a native Jetpack Compose client for our private tinc mesh, based on tincapp 0.45.
The Android application ID is `dev.yuting.tinc`; it installs alongside the
original app. Android 8.0 or later and a 64-bit ARM or x86 device are required.

## Interface

- **Home:** network selection, connect/disconnect, VPN address, and actual peer status.
  An active VPN with no reachable peers is shown as waiting, rather than connected.
- **Devices:** search, local device identity, live UDP/TCP/forwarded reachability,
  round-trip latency, and daemon details.
- **Networks:** create a network with local key generation, join by invitation or
  QR code, add a public peer configuration, and share this device's public export.
- **Settings:** VPN addresses, routes, DNS, app bypass, light/dark/system appearance,
  and connection logs. Interface changes apply after reconnecting.

The UI uses real configuration and daemon state. There are no simulated peers or
connection controls. Fresh installations begin with an empty network list.
The VPN service has a foreground notification and disconnect action.
Android 13 and later asks for notification permission after the first VPN
connection. Declining it keeps the VPN usable; Settings includes a link to
Android's notification controls.

## Native engine

The build downloads unmodified upstream tinc at the same revision as our desktop
trial. It uses tinc's existing Android file-descriptor backend and built-in
SPTPS/Ed25519 crypto. No native patches or separate crypto-library builds are
required. This build supports **tinc 1.1 peers with compression disabled**; it
excludes tinc 1.0/RSA compatibility. See [UPSTREAM.md](UPSTREAM.md) for source
revisions, attribution, and update instructions.

## Build

Install JDK 17, Android SDK platforms `android-37.0`, build tools 37.0.0,
NDK 28.2.13676358, and CMake 3.22.1. Put Meson and Ninja on `PATH`, for example:

```sh
python3 -m venv ../runtime/android-tools
../runtime/android-tools/bin/pip install meson ninja
export PATH="$PWD/../runtime/android-tools/bin:$PATH"
```

Set `JAVA_HOME` and `ANDROID_HOME` for your installation, then:

```sh
./gradlew :app:assembleDebug
```

The resulting APK is `app/build/outputs/apk/debug/app-debug.apk`.
It contains ARM64 and x86-64 binaries. Install it with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is signed using the local Android debug key. A production release
needs our release signing key. No publishing or registry upload is part of this
local build.

## Verify

Use a dedicated Android emulator with no saved networks in this app. The test
journey creates `home`, generates real Ed25519 keys, edits DNS, checks appearance
persistence, declines and then grants Android VPN permission, starts the native
VPN without root, declines optional notification permission, rotates/recreates
the activity, backgrounds the app, and
finally disconnects. Unit tests check actual daemon status parsing and repeated
configuration saves preserving DNS/routes/app bypass.

```sh
ANDROID_SERIAL=DEVICE_SERIAL ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

The emulator must expose its native VPN permission dialog; the test interacts
with Android's normal grant/decline buttons. Real-phone battery/OEM behavior and
camera scanning still require device testing. Connection validation is recorded in [../TRIAL.md](../TRIAL.md).

Source is GPL-3.0-or-later; the upstream license and copyright notices are retained.
Native tinc's GPL license is also bundled in the APK.
