# Upstream sources

This Android client is a local fork of [tincapp](https://github.com/pacien/tincapp)
at `e38cbfbb59deeebe93043af0b9ebf21ebb825e6a` (version 0.45).
Its GPL-3.0-or-later license, contributor list, and copyright notices are retained.
The old activities and XML screen layouts have been replaced with Jetpack Compose.
The rootless service, configuration readers, and command integration derive from
tincapp. Android changes belong in this application, separately from the engine.

`app/CMakeLists.txt` downloads **unmodified upstream tinc** at
`211e3dfaef32d8736962e25f0b096dad951b7104`, the same revision as the Mac/Linux trial.
No tinc patches are applied. tinc's upstream `DeviceType=fd` backend receives the
Android VPN interface over a Unix socket. The Android package uses a separate
application ID, `dev.yuting.tinc`, so it can coexist with the upstream app.

The Android engine uses upstream's `crypto=nolegacy` build: it supports tinc 1.1
SPTPS/Ed25519 peers, including our Mac and Docker builds. It does not support the
tinc 1.0 RSA protocol. Compression libraries are disabled; peers must use
`Compression = 0`, which is the default in our trial. This avoids maintaining
separate Android builds of OpenSSL/LibreSSL and compression libraries.

To update the engine, change the source revision in `app/CMakeLists.txt`, update
the displayed version in `MeshApp.kt`, and run the Android verification and a
connection test against the desktop peers. Keep engine source changes upstream.
