# Tincer

Tincer connects Android phones and Linux hosts through a private tinc mesh VPN.
The Android app works without root, with a Material 3 interface and an unmodified
upstream tinc engine. The Linux Docker setup generates keys and invitations for you.

## Quick start

You need a **Linux host** with Git, Docker Engine, Docker Compose, and `/dev/net/tun`,
plus permission to run Docker. The phone needs **Android 8.0 or later** on a 64-bit
ARM or x86 device. Docker Desktop on macOS does not create a VPN on the Mac host.

### 1. Start the Docker node

On the Linux host:

```sh
git clone https://github.com/YutingXu/Tincer.git
cd Tincer
./setup.sh --address 192.168.1.10 --name home --invite my_phone
```

Replace `192.168.1.10` with an IP address or hostname your phone can reach.
For access outside your LAN, use a public hostname/IP and forward **TCP and UDP
port 655** to the host. Allow the same port through its firewall. Setup does not
configure the router or firewall.

The first run builds the image, generates persistent keys, starts tinc, and
prints a single-use invitation with a QR code. The QR image is also saved as
`pairing/qr.png`. No manual OpenSSL commands or key-file exchange are needed.

### 2. Install Tincer on Android

A prebuilt APK is not published yet. [Build and install the Android app](android/README.md#build),
then return here to pair it with the Docker node.

### 3. Pair and connect

1. Open Tincer and choose **Join network**.
2. Enter a local profile name, then scan the QR code or paste the invitation.
3. Tap **Join**, then **Connect**, and accept Android's VPN permission.

The phone generates its own keys and receives its VPN address and route
automatically. The Docker node uses `10.77.0.1`; each phone gets its own address.
The Home screen shows reachable peers and their connection paths.
Only traffic to `10.77.0.0/24` uses this VPN; it does not tunnel all internet traffic.

## Add another phone

Run this in the same repository directory on the Linux host:

```sh
./setup.sh --invite second_phone
```

Use a different device name for each phone (letters, digits, or underscores).
Repeated setup preserves keys and settings. Reusing a name redisplays its unused
invitation; invitations expire after seven days, and rerunning renews them.
Once a phone has joined, use a new name for the next phone. Keep unused
invitations and QR codes private.

## Manage the node

From the repository directory:

```sh
docker compose logs --tail=50 tinc  # Show recent logs
docker compose stop tinc           # Stop the node
docker compose up -d tinc          # Start the node
```

Back up `config/` to preserve the node's keys and settings. Existing manual tinc
profiles need a separate checkout/config directory.

On the first setup, `--subnet 10.78.0.0/24` selects a different VPN subnet, and
`--port 443` selects another free listening port. Use a subnet that does not
overlap your existing networks. The selected port must be reachable from the phone.

## Compatibility and further help

The app supports **tinc 1.1 peers with compression disabled**, including this
container. The pinned engine is upstream prerelease software. tinc tries direct
UDP, falls back to TCP, and can forward traffic through other peers. TCP port
443 still carries tinc traffic, not HTTPS; a network may filter it.

- [Android build, interface, and testing](android/README.md)
- [Container reference: Linux peers, network options, and maintenance](docs/container.md)
- [Connection validation and remaining device checks](TRIAL.md)
- [Upstream sources and attribution](android/UPSTREAM.md)

The Android app is [GPL-3.0-or-later](android/license.md). Native tinc is
GPL-2.0-or-later with the upstream OpenSSL exception; see the
[container license notes](docs/container.md#license-and-references).
