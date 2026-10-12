#!/bin/sh
set -eu

if [ "$#" -eq 0 ] || [ "$1" = --help ]; then
  cat <<'USAGE'
Usage: ./setup.sh --address HOST [--name NAME] [--subnet CIDR] [--port PORT] [--invite NAME]

Create a Linux Docker tinc node and pair a phone with Tincer.
HOST must be an IP address or hostname reachable from the phone.

Defaults: --name docker_node --subnet 10.77.0.0/24 --port 655 --invite android_phone

Examples:
  ./setup.sh --address 192.168.1.10
  ./setup.sh --address vpn.example.com --name home --invite my_phone
  ./setup.sh --invite second_phone

The last example reuses an existing setup. Keys and settings persist in ./config.
USAGE
  exit 0
fi

if [ "$(uname -s)" != Linux ]; then
  echo 'Run this setup on the Linux Docker host; its VPN interface uses Linux host networking.' >&2
  exit 1
fi

cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
umask 077

docker compose build tinc
docker compose run --rm --no-deps --entrypoint tincer-setup tinc prepare "$@"
docker compose up -d tinc
docker compose exec -T tinc tincer-setup invite "$@"
mkdir -p pairing
docker compose exec -T tinc tincer-setup png "$@" > pairing/qr.png
echo 'QR image saved to pairing/qr.png. In Tincer, choose Join network and scan or paste the invitation.'
