#!/usr/bin/env python3
"""Initialize a persistent tinc node and issue addressed Tincer invitations."""

import argparse
import ipaddress
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time


NETWORK = os.environ.get("TINC_NETWORK", "mesh")
PROFILE = Path("/etc/tinc") / NETWORK
SETTINGS = PROFILE / "tincer.json"
INVITATION_LIFETIME = 604800  # tinc's default: seven days


def tinc(*args):
    result = subprocess.run(
        ["tinc", "-b", "-n", NETWORK, *args], capture_output=True, text=True,
    )
    if result.returncode:
        raise ValueError(result.stderr.strip() or result.stdout.strip())
    return result.stdout.strip()


def save(settings):
    SETTINGS.write_text(json.dumps(settings, indent=2) + "\n")


def prepare(args):
    requested = {key: getattr(args, key) for key in ("name", "address", "subnet", "port")}
    if SETTINGS.exists():
        settings = json.loads(SETTINGS.read_text())
        for key, value in requested.items():
            if value is not None and value != settings[key]:
                raise ValueError(f"This node already uses {key}={settings[key]}; omit --{key} to reuse it.")
    else:
        if (PROFILE / "tinc.conf").exists():
            raise ValueError("An existing manual tinc configuration is present. Use a separate config directory for this setup.")
        if not args.address:
            raise ValueError("First setup needs --address with an IP or hostname reachable from the phone.")
        settings = {
            "name": args.name or "docker_node",
            "address": args.address,
            "subnet": args.subnet or "10.77.0.0/24",
            "port": args.port or 655,
            "peers": {},
        }
        network = ipaddress.IPv4Network(settings["subnet"])
        server = next(network.hosts())
        tinc("init", settings["name"])
        for key, value in (
            ("DeviceType", "tun"), ("Interface", "tinc0"),
            ("Port", str(settings["port"])),
            (f"{settings['name']}.Address", settings["address"]),
            (f"{settings['name']}.Subnet", f"{server}/32"),
        ):
            tinc("set", key, value)
        hook = PROFILE / "tinc-up"
        hook.write_text(
            '#!/bin/sh\nset -eu\n'
            f'ip address replace {server}/{network.prefixlen} dev "$INTERFACE"\n'
            'ip link set "$INTERFACE" up\n'
        )
        hook.chmod(0o755)
        save(settings)
    print(f"Using node {settings['name']} on {NETWORK} ({settings['subnet']}).", flush=True)


def first_block(path):
    """Read only the invitee block, excluding the inviter's configuration."""
    fields = {}
    for line in path.read_text().splitlines():
        key, separator, value = line.partition("=")
        if not separator:
            continue
        key, value = key.strip(), value.strip()
        if key == "Name" and key in fields:
            break
        fields.setdefault(key, []).append(value)
    return fields


def allocate(settings, peer):
    if peer in settings["peers"]:
        return settings["peers"][peer]["address"]
    network = ipaddress.IPv4Network(settings["subnet"])
    reserved = [ipaddress.IPv4Network(f"{next(network.hosts())}/32")]
    reserved.extend(ipaddress.IPv4Network(f"{entry['address']}/32") for entry in settings["peers"].values())
    for path in (PROFILE / "hosts").iterdir():
        for subnet in first_block(path).get("Subnet", []):
            block = ipaddress.ip_network(subnet.split("#", 1)[0], strict=False)
            if block.version == 4:
                reserved.append(block)
    invitations = PROFILE / "invitations"
    if invitations.exists():
        for path in invitations.iterdir():
            if len(path.name) == 24:
                reserved.extend(
                    ipaddress.IPv4Network(subnet)
                    for subnet in first_block(path).get("Subnet", [])
                )
    for address in network.hosts():
        if all(address not in block for block in reserved):
            return str(address)
    raise ValueError(f"No free VPN addresses in {network}.")


def invite(args):
    settings = json.loads(SETTINGS.read_text())
    peer = args.invite
    if (PROFILE / "hosts" / peer).exists():
        raise ValueError(f"{peer} is already paired. Use --invite with a different name for another device.")

    # Compose can return before tinc opens its control socket.
    deadline = time.monotonic() + 15
    while True:
        try:
            tinc("pid")
            break
        except ValueError:
            if time.monotonic() >= deadline:
                raise ValueError("tinc did not start. Check docker compose logs tinc.")
            time.sleep(0.2)

    entry = settings["peers"].get(peer)
    invitation_dir = PROFILE / "invitations"
    pending = invitation_dir / entry["file"] if entry else None
    if not pending or not pending.exists() or pending.stat().st_mtime + INVITATION_LIFETIME <= time.time():
        address = allocate(settings, peer)
        before = set(invitation_dir.iterdir()) if invitation_dir.exists() else set()
        url = tinc("invite", peer)
        pending, = set(invitation_dir.iterdir()) - before - {invitation_dir / "ed25519_key.priv"}
        contents = pending.read_text()
        name, rest = contents.split("\n", 1)
        pending.write_text(
            f"{name}\nSubnet = {address}/32\nIfconfig = {address}/32\n"
            f"Route = {settings['subnet']}\n{rest}"
        )
        entry = {"address": address, "url": url, "file": pending.name}
        settings["peers"][peer] = entry
        save(settings)
    pairing = PROFILE / "pairing"
    pairing.mkdir(exist_ok=True)
    subprocess.run(["qrencode", "-o", str(pairing / f"{peer}.png"), entry["url"]], check=True)
    print(f"\nPair {peer}: VPN address {entry['address']}/32, route {settings['subnet']}.", flush=True)
    print(f"Invitation (private, single use; expires seven days after creation):\n{entry['url']}\n", flush=True)
    subprocess.run(["qrencode", "-t", "ANSIUTF8", entry["url"]], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("prepare", "invite", "png"))
    parser.add_argument("--address", help="IP or hostname reachable from the phone")
    parser.add_argument("--name", help="unique Docker node name (default: docker_node)")
    parser.add_argument("--subnet", help="IPv4 VPN subnet (default: 10.77.0.0/24)")
    parser.add_argument("--port", type=int, help="TCP/UDP listen port (default: 655)")
    parser.add_argument("--invite", default="android_phone", help="unique phone name")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_-]+", NETWORK):
        parser.error("TINC_NETWORK must contain only letters, digits, underscores or hyphens.")
    for name in (args.name, args.invite):
        if name is not None and not re.fullmatch(r"[A-Za-z0-9_]+", name):
            parser.error("Node names must contain only letters, digits or underscores.")
    if args.address is not None and (not args.address or re.search(r"[\s/]", args.address)):
        parser.error("--address must be an IP or hostname, without a URL scheme or spaces.")
    if args.subnet is not None:
        network = ipaddress.IPv4Network(args.subnet)
        if network.prefixlen > 30:
            parser.error("--subnet needs room for the Docker node and at least one phone (maximum /30).")
        args.subnet = str(network)
    if args.port is not None and not 1 <= args.port <= 65535:
        parser.error("--port must be between 1 and 65535.")
    os.umask(0o077)
    if args.action == "prepare":
        prepare(args)
    elif args.action == "invite":
        invite(args)
    else:
        sys.stdout.buffer.write((PROFILE / "pairing" / f"{args.invite}.png").read_bytes())


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f"tincer-setup: {error}", file=sys.stderr)
        sys.exit(1)
