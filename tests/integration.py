#!/usr/bin/env python3
"""Exercise the production image using isolated Docker peers and fresh keys."""

import json
import subprocess
import sys
import time
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def docker(*args, stdin=None, check=True, timeout=60):
    result = subprocess.run(
        ["docker", *args], input=stdin, capture_output=True, text=True,
        cwd=ROOT, timeout=timeout,
    )
    if check and result.returncode:
        raise RuntimeError(
            f"docker {' '.join(args)} failed:\n{result.stdout}{result.stderr}"
        )
    return result.stdout.strip()


def main():
    image = sys.argv[1] if len(sys.argv) > 1 else "tinc-private:local"
    prefix = f"tinc-test-{uuid.uuid4().hex[:10]}"
    firewall_image = f"{prefix}:firewall"
    nodes = ("alpha", "beta")
    containers = {node: f"{prefix}-{node}" for node in nodes}
    volumes = {node: f"{prefix}-{node}-config" for node in nodes}
    addresses = {"alpha": "10.77.0.1", "beta": "10.77.0.2"}

    def mount(node):
        return f"type=volume,source={volumes[node]},target=/etc/tinc"

    def admin(node, *args, stdin=None):
        return docker(
            "run", "--rm", "-i", "--network", "none", "--mount", mount(node),
            "--entrypoint", "tinc", image, "-b", "-n", "mesh", *args,
            stdin=stdin,
        )

    def firewall(node, *args):
        return docker(
            "run", "--rm", "--network", f"container:{containers[node]}",
            "--cap-add", "NET_ADMIN", "--entrypoint", "iptables",
            firewall_image, *args,
        )

    def wait_for_peer(description, ready):
        deadline = time.monotonic() + 40
        status = ""
        while time.monotonic() < deadline:
            docker(
                "exec", containers["alpha"], "ping", "-c", "1", "-W", "1",
                addresses["beta"], check=False,
            )
            status = docker(
                "exec", containers["alpha"], "tinc", "-n", "mesh", "info",
                "beta", check=False,
            )
            if ready(status):
                return
            time.sleep(0.5)
        raise RuntimeError(f"Peer did not reach {description}:\n{status}")

    def ping_both_ways():
        for source, destination in (("alpha", "beta"), ("beta", "alpha")):
            docker(
                "exec", containers[source], "ping", "-c", "3", "-W", "2",
                addresses[destination],
            )

    try:
        print("Preparing an isolated firewall helper...", flush=True)
        docker(
            "build", "--tag", firewall_image, "--build-arg", f"TINC_IMAGE={image}",
            "-", stdin=(
                "ARG TINC_IMAGE\nFROM ${TINC_IMAGE}\n"
                "RUN apk add --no-cache iptables zbar imagemagick\n"
            ), timeout=300,
        )
        docker("network", "create", prefix)

        for node in nodes:
            docker("volume", "create", volumes[node])

        def prepare(*args):
            return docker(
                "run", "--rm", "--network", "none", "--mount", mount("alpha"),
                "--entrypoint", "tincer-setup", image, "prepare", *args,
            )

        prepare("--address", "alpha", "--name", "alpha")
        identity = admin("alpha", "export")
        prepare("--address", "alpha", "--name", "alpha")
        if admin("alpha", "export") != identity:
            raise RuntimeError("Rerunning setup replaced the node's identity")
        print("PASS: first setup generates keys; rerunning preserves identity", flush=True)

        admin("alpha", "-n", "manual", "init", "existing_node")
        manual_identity = admin("alpha", "-n", "manual", "export")
        try:
            docker(
                "run", "--rm", "--network", "none", "--mount", mount("alpha"),
                "--env", "TINC_NETWORK=manual", "--entrypoint", "tincer-setup", image,
                "prepare", "--address", "alpha",
            )
        except RuntimeError as error:
            if "existing manual tinc configuration" not in str(error):
                raise
        else:
            raise RuntimeError("Setup overwrote an existing manual configuration")
        if admin("alpha", "-n", "manual", "export") != manual_identity:
            raise RuntimeError("Setup changed the existing manual identity")
        print("PASS: existing manual configurations remain intact", flush=True)

        def start(node):
            docker(
                "run", "--detach", "--name", containers[node], "--network", prefix,
                "--network-alias", node, "--cap-add", "NET_ADMIN",
                "--device", "/dev/net/tun:/dev/net/tun", "--mount", mount(node),
                "--env", f"VPN_ADDRESS={addresses[node]}/24", image,
            )

        start("alpha")

        def pair(peer):
            docker("exec", containers["alpha"], "tincer-setup", "invite", "--invite", peer)
            settings = json.loads(docker("exec", containers["alpha"], "cat", "/etc/tinc/mesh/tincer.json"))
            return settings["peers"][peer]

        beta = pair("beta")
        invitation = docker(
            "exec", containers["alpha"], "cat", f"/etc/tinc/mesh/invitations/{beta['file']}",
        )
        for line in ("Name = beta", "Subnet = 10.77.0.2/32", "Ifconfig = 10.77.0.2/32", "Route = 10.77.0.0/24"):
            if line not in invitation.splitlines():
                raise RuntimeError(f"Invitation is missing {line}")
        if pair("beta") != beta:
            raise RuntimeError("Rerunning pairing did not reuse the outstanding invitation")
        gamma = pair("gamma")
        if gamma["address"] != "10.77.0.3":
            raise RuntimeError("Unused invitations did not reserve separate phone addresses")

        # Renew a real expired native invitation, preserving the phone's address.
        docker(
            "exec", containers["alpha"], "python3", "-c",
            "import os,sys; os.utime(sys.argv[1], (0, 0))",
            f"/etc/tinc/mesh/invitations/{beta['file']}",
        )
        renewed = pair("beta")
        if renewed["address"] != beta["address"] or renewed["url"] == beta["url"]:
            raise RuntimeError("Expired invitation was not renewed with its reserved address")
        beta = renewed
        decoded = docker(
            "run", "--rm", "--network", "none", "--mount", mount("alpha") + ",readonly",
            "--entrypoint", "zbarimg", firewall_image, "--quiet", "--raw",
            "/etc/tinc/mesh/pairing/beta.png",
        )
        if decoded != beta["url"]:
            raise RuntimeError("QR image did not decode to the invitation URL")
        print("PASS: addressed invitations, QR decoding, pending reservations and expiration renewal", flush=True)

        docker(
            "run", "--rm", "--network", prefix, "--mount", mount("beta"),
            "--entrypoint", "tinc", image, "-b", "--force", "-n", "mesh", "join", beta["url"],
        )
        joined = admin("beta", "export")
        if any(value not in joined for value in ("Subnet = 10.77.0.2/32", "Ed25519PublicKey")):
            raise RuntimeError("Invitation join did not produce an addressed peer identity")
        delta = pair("delta")
        if delta["address"] != "10.77.0.4":
            raise RuntimeError("Joined phones lost their reserved address")

        # Keep transport discovery short in this isolated test only.
        for node in nodes:
            admin(node, "set", "UDPDiscoveryTimeout", "5")
            admin(node, "set", "UDPDiscoveryKeepaliveInterval", "1")
        start("beta")
        exports = {}
        wait_for_peer("direct UDP", lambda status: "directly with UDP" in status)
        ping_both_ways()
        print("PASS: invitation-paired peers configure their interfaces and pass direct UDP traffic", flush=True)

        for node in nodes:
            firewall(node, "-I", "INPUT", "-p", "udp", "--dport", "655", "-j", "DROP")
        wait_for_peer("TCP fallback", lambda status: "directly with TCP" in status)
        ping_both_ways()
        for node in nodes:
            counters = firewall(node, "-L", "INPUT", "-n", "-v", "-x")
            dropped = sum(
                int(line.split()[0]) for line in counters.splitlines()
                if "DROP" in line and "udp" in line
            )
            if not dropped:
                raise RuntimeError(f"{node} firewall did not block any UDP packets")
        print("PASS: blocking UDP triggers automatic TCP fallback", flush=True)

        for node in nodes:
            firewall(node, "-D", "INPUT", "-p", "udp", "--dport", "655", "-j", "DROP")
            docker("exec", containers[node], "tinc", "-n", "mesh", "set", "TCPOnly", "yes")
            exports[node] = docker("exec", containers[node], "tinc", "-n", "mesh", "export")
        docker("restart", "-t", "5", *containers.values())
        wait_for_peer(
            "TCP-only mode",
            lambda status: " tcponly" in status and " reachable" in status,
        )
        ping_both_ways()
        print("PASS: explicit TCPOnly configuration passes traffic", flush=True)

        for node in nodes:
            exported = docker("exec", containers[node], "tinc", "-n", "mesh", "export")
            if exported != exports[node]:
                raise RuntimeError(f"{node} identity or host configuration changed on restart")
        print("PASS: peer identities persist across restarts", flush=True)

        docker("stop", "-t", "5", *containers.values())
        for node in nodes:
            code = docker("inspect", "--format", "{{.State.ExitCode}}", containers[node])
            if code != "0":
                raise RuntimeError(f"{node} did not shut down cleanly: exit {code}")
        print("PASS: both daemons stop cleanly on SIGTERM", flush=True)
    except Exception:
        for node in nodes:
            print(f"{node} logs:", file=sys.stderr)
            subprocess.run(
                ["docker", "logs", "--tail", "60", containers[node]],
                stdout=sys.stderr, stderr=sys.stderr, timeout=10,
            )
        raise
    finally:
        for container in containers.values():
            docker("rm", "--force", container, check=False)
        docker("network", "rm", prefix, check=False)
        for volume in volumes.values():
            docker("volume", "rm", volume, check=False)
        docker("image", "rm", firewall_image, check=False)


if __name__ == "__main__":
    main()
