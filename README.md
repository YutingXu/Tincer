# Private tinc container

A small, source-built tinc image for a private mesh. Peers use direct UDP when
available and fall back to carrying VPN packets over TCP connections when UDP is
blocked. Peers can also forward traffic for one another without depending on
Tailscale's relay infrastructure.

The image pins tinc's **1.1 development branch at
`211e3dfaef32d8736962e25f0b096dad951b7104` (27 June 2026)** and uses Alpine 3.24.
This snapshot includes fixes after the published 1.1pre18 release. The binary
still reports 1.1pre18; the image's revision label identifies the actual source.
The 1.1 branch also supplies the control and invitation tools used by mobile
clients. It is upstream prerelease software.

Only the compiled daemon, control utility, runtime libraries, interface tools,
and license notices enter the final image. Configuration and keys live in
`./config`, mounted at `/etc/tinc`, and are excluded from the build context.

## Build

Docker Engine or Docker Desktop with Compose is required:

```sh
docker compose build --pull
docker run --rm tinc-private:local --version
```

The same Dockerfile builds on Linux ARM64 and AMD64. To build another architecture:

```sh
docker buildx build --platform linux/amd64 --load -t tinc-private:amd64 .
```

## Connect two Linux hosts

The supplied Compose service uses **Linux host networking** so the VPN interface
belongs to the host. It needs `/dev/net/tun` and `NET_ADMIN`. It does not need
`privileged: true`.

Use a non-overlapping VPN subnet; the following example uses `10.77.0.0/24`.
Clone or copy this directory onto both hosts and build the image on each.

On **node A**, initialize a persistent identity:

```sh
docker compose run --rm --no-deps --entrypoint tinc tinc -b -n mesh init node_a
docker compose run --rm --no-deps --entrypoint tinc tinc -b -n mesh set Interface tinc0
docker compose run --rm --no-deps --entrypoint tinc tinc -b -n mesh set DeviceType tun
docker compose run --rm --no-deps --entrypoint tinc tinc -b -n mesh set node_a.Subnet 10.77.0.1/32
docker compose run --rm --no-deps --entrypoint tinc tinc -b -n mesh set node_a.Address NODE_A_REACHABLE_ADDRESS
docker compose run --rm --no-deps --entrypoint tinc tinc -b -n mesh set ConnectTo node_b
docker compose run --rm -T --no-deps --entrypoint sh tinc \
  -c 'cat > /etc/tinc/mesh/tinc-up && chmod 755 /etc/tinc/mesh/tinc-up' < examples/tinc-up
printf 'VPN_ADDRESS=10.77.0.1/24\n' > .env
```

On **node B**:

```sh
docker compose run --rm --no-deps --entrypoint tinc tinc -b -n mesh init node_b
docker compose run --rm --no-deps --entrypoint tinc tinc -b -n mesh set Interface tinc0
docker compose run --rm --no-deps --entrypoint tinc tinc -b -n mesh set DeviceType tun
docker compose run --rm --no-deps --entrypoint tinc tinc -b -n mesh set node_b.Subnet 10.77.0.2/32
docker compose run --rm --no-deps --entrypoint tinc tinc -b -n mesh set node_b.Address NODE_B_REACHABLE_ADDRESS
docker compose run --rm --no-deps --entrypoint tinc tinc -b -n mesh set ConnectTo node_a
docker compose run --rm -T --no-deps --entrypoint sh tinc \
  -c 'cat > /etc/tinc/mesh/tinc-up && chmod 755 /etc/tinc/mesh/tinc-up' < examples/tinc-up
printf 'VPN_ADDRESS=10.77.0.2/24\n' > .env
```

Replace the address placeholders with real reachable hostnames or IP addresses.
At least one peer must be reachable by the other. For a peer behind NAT with no
reachable address, omit its `Address` setting and initiate `ConnectTo` towards a
reachable peer. Allow TCP 655 and, where possible, UDP 655 on reachable peers;
forward those ports through their upstream router if necessary.

Export **only the public host information** on each host:

```sh
docker compose run --rm -T --no-deps --entrypoint tinc tinc -b -n mesh export > node_a.public
```

Use `node_b.public` as the output name on B. Exchange these two files through a
channel you trust, then import B's file on A:

```sh
docker compose run --rm -T --no-deps --entrypoint tinc tinc -b -n mesh import < node_b.public
```

On B, import `node_a.public` instead. All configuration writes run inside the
container because the mounted files are owned by root on Linux.
Keep `rsa_key.priv` and `ed25519_key.priv` on their originating host. These are
created at initialization, never baked into the image, and reused on restart.

Start both peers:

```sh
docker compose up -d
docker compose logs --tail=50 tinc
docker compose exec tinc tinc -n mesh dump reachable nodes
```

From node A, `ping 10.77.0.2`; from node B, `ping 10.77.0.1`.
This example connects the hosts themselves. Access to another LAN behind a peer
requires advertising that subnet and configuring forwarding and return routes.

## Restricted networks

Leave UDP discovery enabled for automatic UDP/TCP selection. To inspect the
transport from node A:

```sh
docker compose exec tinc tinc -n mesh info node_b
```

For a network that consistently blocks UDP, explicitly select TCP on that peer:

```sh
docker compose exec tinc tinc -n mesh set TCPOnly yes
docker compose restart tinc
```

Explicit TCP-only mode appears as `tcponly` in the `Options` line of `tinc info`.

If the firewall permits TCP 443, a reachable peer can listen on that port:

```sh
docker compose exec tinc tinc -n mesh set Port 443
docker compose restart tinc
```

Update that peer's public host file on the other nodes to include `Port = 443`.
With host networking, the selected port must be free on the host. `EXPOSE 655`
is image metadata and does not restrict the daemon's actual listen port.
**tinc on port 443 is still tinc traffic, not HTTPS.** It gives another connection
path when the network permits it; it does not provide TLS camouflage or guarantee
passage through protocol inspection. TCP fallback can also reduce throughput.

For more than two peers, configure one or more reachable bootstrap peers. tinc
learns the mesh through those connections, attempts direct peer connections, and
forwards packets through other peers when a direct path is unavailable.

## Administration and maintenance

Use `docker compose exec tinc tinc -n mesh ...` while the daemon is running.
For offline configuration, use
`docker compose run --rm --no-deps --entrypoint tinc tinc -b -n mesh ...`.
Initialization creates a new configuration once; ordinary rebuilds and restarts
keep using the mounted configuration.

Rebuild against current Alpine packages:

```sh
docker compose build --pull --no-cache
python3 tests/integration.py
docker compose up -d
```

To update tinc itself, change the pinned `TINC_REF` in the Dockerfile after
reviewing upstream changes, update the revision in this README, then build and
run the integration test. A build argument can also select a particular upstream
revision: `docker build --build-arg TINC_REF=REVISION -t tinc-private:local .`.
Use versioned image tags when promoting a tested build to your private registry.

## Integration test

```sh
docker build -t tinc-private:local .
python3 tests/integration.py tinc-private:local
```

The test creates two isolated peers with fresh keys and checks direct UDP traffic,
automatic TCP fallback after firewall rules block UDP, explicit TCP-only traffic,
identity persistence on restart, and clean SIGTERM shutdown. A disposable helper
image supplies `iptables`; the production image is exercised unchanged. Containers,
volumes, the Docker network, and the helper image are removed afterwards.

This test works in Docker Desktop's Linux VM, including on macOS. The deployment
Compose file is for native Linux: running a Linux container on macOS does not
create a tinc VPN interface in the macOS host network.

## License and references

tinc is GPL-2.0-or-later, with the OpenSSL exception in `COPYING.README`. Notices
are included at `/usr/share/licenses/tinc`. When distributing this image, supply
the corresponding tinc source and build instructions under its license.

- [Upstream source](https://github.com/gsliepen/tinc/tree/211e3dfaef32d8736962e25f0b096dad951b7104)
- [How connections work](https://www.tinc-vpn.org/documentation-1.1/How-connections-work.html)
- [Configuration reference](https://www.tinc-vpn.org/documentation-1.1/tinc.conf.5.html)
