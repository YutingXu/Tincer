# Tincer for Android

Tincer is a rootless Jetpack Compose client for tinc private networks, based on tincapp 0.45.
The Android application ID is `dev.yuting.tinc`; it installs alongside the
original app. Android 8.0 or later and a 64-bit ARM or x86 device are required.

## Interface

Material 3, with wallpaper colours on Android 12 and later and Tincer's indigo before that.

- **Home:** the selected network's state, this device's VPN address, a connect toggle,
  and every peer with live UDP/TCP/forwarded reachability and round-trip latency.
  An active VPN with no reachable peers is shown as waiting, rather than connected.
  Tap the network name to switch networks, join by invitation or QR code, or create one
  with local key generation. Adding a peer imports its public export and offers this
  device's export to share back. Tap a peer for its endpoint, traffic and daemon details.
- **Settings:** VPN addresses, routes, DNS, excluded apps, public key sharing,
  light/dark/system appearance, notifications, and the connection log.
  Interface changes apply after reconnecting.

The theme is Material 3 Expressive (`MaterialExpressiveTheme` with the expressive motion
scheme), which needs material3 `1.5.0-beta01`, pinned in `app/build.gradle` ahead of the
Compose BOM. The connect toggle morphs from a circle into a scalloped shape, a peer row
expands into its page, settings slides in on a shared axis, and both pages follow
Android's predictive back gesture.

The UI uses real configuration and daemon state. There are no simulated peers or
connection controls. Fresh installations begin with an empty network list.
The VPN service has a foreground notification and disconnect action.
Android 13 and later asks for notification permission after the first VPN
connection. Declining it keeps the VPN usable; Settings includes a link to
Android's notification controls.

The Docker [quick start](../README.md#quick-start)
prints an invitation and QR code with the phone's VPN address and route.
Joining selects an ephemeral listening port, since Android cannot bind tinc's
privileged default port 655. tinc filters port settings out of invitations, so
the app sets its own port after the native join completes.

## Native engine

The build downloads unmodified upstream tinc at the same revision as the Docker
image. It uses tinc's existing Android file-descriptor backend and built-in
SPTPS/Ed25519 crypto. No native patches or separate crypto-library builds are
required. This build supports **tinc 1.1 peers with compression disabled**; it
excludes tinc 1.0/RSA compatibility. See [UPSTREAM.md](UPSTREAM.md) for source
revisions, attribution, and update instructions.

## Build

Prebuilt APKs are not published yet. To build locally, install Python 3,
JDK 17, Android SDK platform `android-37.0`, build tools 37.0.0,
NDK 28.2.13676358, and CMake 3.22.1. From the repository root, install Meson and
Ninja in a local environment and add them to `PATH`:

```sh
cd android
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

For USB installation, enable USB debugging on the phone and approve the computer
when prompted. After installing, follow the [pairing steps](../README.md#3-pair-and-connect).

The debug APK uses your local Android debug key. Production distribution needs
a release signing key; this build does not publish an APK.

The native tools are staged on every CMake build, including when the upstream
build is cached. To verify that both tools remain in the APK for both ABIs after
a Gradle clean that retains the native cache, run from this directory:

```sh
python3 ../tests/android_packaging.py
```

## Verify

Use a dedicated Android emulator with no saved networks in this app. The test
journey creates `home`, generates real Ed25519 keys, edits DNS, preserves pending
app exclusions and their search across activity recreation, checks appearance
persistence, declines and then grants Android VPN permission, starts the native
VPN without root, declines optional notification permission, rotates/recreates
the activity, backgrounds the app, and finally disconnects. Peer-search UI
regressions check that switching networks clears the query and that an active
search remains available to clear after disconnecting. Unit tests
check actual daemon status parsing and repeated configuration saves preserving
DNS/routes/app bypass.

```sh
ANDROID_SERIAL=DEVICE_SERIAL ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

Replace `DEVICE_SERIAL` with the serial shown by `adb devices`.
The emulator must expose its native VPN permission dialog; the test interacts
with Android's normal grant/decline buttons. Long-running battery behavior and
camera scanning still require device testing. Connection validation is recorded in [../TRIAL.md](../TRIAL.md).

To test real invitation enrollment and VPN traffic, install the current debug
APK on a fresh dedicated emulator or test phone, generate an unused invitation
with the Docker setup, and save the URL in a private file:

```sh
ANDROID_SERIAL=DEVICE_SERIAL python3 ../tests/android_invitation.py \
  --invitation-file /path/to/invitation.txt --peer 10.77.0.1
```

The test uses the normal Join and Connect screens, accepts VPN consent if
needed, declines optional notifications, and requires five 1,300-byte pings
to the Docker node with no loss after transport discovery. It leaves the test
profile connected for inspection and does not erase existing app data.

Source is GPL-3.0-or-later; the upstream license and copyright notices are retained.
Native tinc's GPL license is also bundled in the APK.
