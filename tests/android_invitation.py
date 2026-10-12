#!/usr/bin/env python3
"""Join and connect Tincer on a dedicated Android device using a real invitation."""

import argparse
import os
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--invitation-file", type=Path, required=True)
    parser.add_argument("--peer", required=True, help="Docker node's VPN IP to ping")
    parser.add_argument("--profile", default="pairing_test")
    args = parser.parse_args()
    serial = os.environ.get("ANDROID_SERIAL")
    if not serial:
        parser.error("Set ANDROID_SERIAL to a dedicated emulator or test phone.")

    def adb(*command):
        return subprocess.check_output(["adb", "-s", serial, *command], text=True, timeout=30)

    def nodes():
        adb("shell", "uiautomator", "dump", "/sdcard/tincer-invitation-test.xml")
        return list(ET.fromstring(adb("shell", "cat", "/sdcard/tincer-invitation-test.xml")).iter("node"))

    def tap(node):
        left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
        adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))

    def wait_for(predicate):
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            current = nodes()
            error = next((node for node in current if node.get("text") == "Couldn’t finish"), None)
            if error is not None:
                raise RuntimeError("Tincer failed: " + "; ".join(node.get("text") for node in current if node.get("text")))
            match = next((node for node in current if predicate(node)), None)
            if match is not None:
                return match
            time.sleep(0.5)
        raise RuntimeError("Expected Tincer screen did not appear. Use a fresh dedicated app installation and a reachable invitation.")

    def text(label):
        return lambda node: node.get("text") == label

    def field(label, value):
        tap(wait_for(text(label)))
        adb("shell", "input", "text", shlex.quote(value.replace(" ", "%s")))

    adb("shell", "am", "start", "-n", "dev.yuting.tinc/org.pacien.tincapp.ui.MainActivity")
    tap(wait_for(text("Join network")))
    field("Network name", args.profile)
    field("Invitation", args.invitation_file.read_text().strip())
    adb("shell", "input", "keyevent", "4")  # Hide the keyboard.
    tap(wait_for(text("Join")))
    wait_for(text("Disconnected"))
    print("PASS: Tincer joined through its invitation UI", flush=True)

    tap(wait_for(lambda node: node.get("content-desc") == "Connect"))
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        current = nodes()
        texts = [node.get("text") for node in current if node.get("text")]
        if "Couldn’t finish" in texts:
            raise RuntimeError("Tincer failed: " + "; ".join(texts))
        permission = next((node for node in current if node.get("resource-id") == "android:id/button1"), None)
        notification = next((node for node in current if node.get("resource-id", "").endswith("permission_deny_button")), None)
        if permission is not None:
            tap(permission)
        elif notification is not None:
            tap(notification)
        elif "Connected" in texts:
            break
        time.sleep(0.5)
    else:
        raise RuntimeError("Tincer did not connect to the Docker peer.")

    # Initial transport discovery may drop a probe; check steady traffic afterward.
    for _ in range(10):
        probe = subprocess.run(
            ["adb", "-s", serial, "shell", "ping", "-c", "1", "-W", "2", args.peer],
            capture_output=True, text=True, timeout=10,
        )
        if probe.returncode == 0:
            break
    else:
        raise RuntimeError("The Docker VPN address did not become reachable.")
    result = adb("shell", "ping", "-c", "5", "-s", "1300", "-W", "3", args.peer)
    print(result, flush=True)
    if not re.search(r"\b5 packets transmitted, 5 (?:packets )?received", result):
        raise RuntimeError("The five steady VPN traffic probes did not all arrive.")
    print("PASS: rootless invitation startup, automatic interface settings and Docker VPN traffic", flush=True)


if __name__ == "__main__":
    main()
