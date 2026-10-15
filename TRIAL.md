# Connection validation

These results were recorded on 11–12 October 2026. Setup and repeatable test
instructions are in [README.md](README.md) and [android/README.md](android/README.md).

## Desktop and container

Native macOS and x86-64 Linux Docker peers established direct UDP connectivity.
Pings with 1,300-byte payloads passed in both directions without loss. When UDP
was temporarily blocked, tinc automatically fell back to TCP and continued to
carry ping and SSH traffic. Removing the block restored UDP connectivity.

The Docker integration suite passes:

- Fresh initialization and key persistence across repeated setup and restarts.
- Preservation of existing manually configured profiles.
- Address allocation for unused and joined invitations, including expiry renewal.
- Independent decoding of the generated QR image.
- Invitation enrollment and direct UDP traffic between peers.
- Automatic TCP fallback, explicit TCP-only operation, and clean shutdown.

The one-command setup was also exercised on a native x86-64 Linux host. It built
and started the container, generated persistent keys, and produced invitations
and terminal/PNG QR codes. Adding further invitations preserved the host identity.

## Android

The Android 16 emulator passed three unit tests, three instrumented UI tests,
and lint. The APK contains both native tools for ARM64 and x86-64, including
after a Gradle clean that retains the CMake cache. Cold VPN startup, permission
handling, settings persistence, peer search, and activity recreation were checked.
Layouts were inspected at 320 dp, normal phone width, and tablet width.

A physical Android 16 phone established direct UDP connections to desktop peers.
Five pings with 1,300-byte payloads passed without loss. The connect control,
peer navigation, light/dark appearance, and predictive-back commit/cancellation
were checked on the phone.

The emulator also joined a Docker node through the normal invitation screen,
received its address and routes, and started the VPN without root. The
[invitation test](tests/android_invitation.py) passed five 1,300-byte pings without
loss. This check used TCP through a temporary SSH forward to a reachable test
endpoint; direct UDP and automatic TCP fallback were verified separately.

Invitation enrollment sets `Port = 0` locally after joining. Upstream filters
port hints from invitations, and rootless Android cannot bind the default port
655. The upstream native engine remains unmodified.

## Remaining device and network checks

Physical-camera QR scanning, long-running battery behavior, and connectivity
from a network that blocks Tailscale relay traffic have not been verified.

Keep raw logs and machine-specific deployment notes in the ignored `runtime/`
directory. Shared test fixtures should use example hostnames and documentation
addresses.
