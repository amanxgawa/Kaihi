# Mock VPS

A throwaway Debian container that stands in for the VPS the setup wizard deploys to, so you can run
the wizard from a phone or an emulator without renting a server. It is a real Linux userland with
systemd, `sshd` and `apt`, not a stub: the wizard's scripts run against it unmodified.

Nothing here is built into the app. "Host" below means the computer that runs Docker.

## Requirements

You need Docker with Compose v2 (`docker compose version` prints a version) and, for a USB phone,
`adb` from the Android SDK platform-tools (`adb devices` lists the phone). The script is bash.

| Host | Setup | Tested |
|---|---|---|
| macOS | Docker Desktop. `adb` from Android Studio or `brew install android-platform-tools`. | Yes, Apple Silicon |
| Linux | Docker Engine with the `docker-compose-plugin`, run as a user in the `docker` group (not with `sudo`, which would leave `.root-password` owned by root). Rootless Docker cannot publish port 443: see [Ports](#ports). | No |
| Windows | WSL 2 with Docker Desktop's WSL integration on. Clone the repository inside the WSL file system, so the script keeps its LF line endings, and run everything from a WSL shell. WSL cannot see USB devices, so install platform-tools on Windows and run with `ADB=adb.exe`. | No |

The untested rows describe what should work. If a step fails on your host, an issue or a pull
request fixing this README is welcome.

## Quick start

```sh
tools/mock-vps/mock-vps.sh up        # build, start, set the root password, forward ports over USB
tools/mock-vps/mock-vps.sh password  # the root password for the wizard
```

On Windows, from WSL: `ADB=adb.exe tools/mock-vps/mock-vps.sh up`.

`up` prints the values to type into the wizard:

| Field | Value |
|---|---|
| Address | depends on how the device reaches the host, see the next section |
| SSH port | `2222` |
| Username | `root` |
| Password | `mock-vps.sh password` |

It also prints the host key fingerprints. The wizard's trust-on-first-use prompt should show one of
them, in the same `SHA256:` format `ssh-keygen` uses, so you can check that the prompt is about this
container.

## Connecting the device

| Device | Address in the wizard | How it gets there |
|---|---|---|
| Phone over USB | `127.0.0.1` | `up` runs `adb reverse`, see below |
| Emulator on the same host | `10.0.2.2` (or `127.0.0.1`, since `up` sets the forwards on an emulator too) | The emulator's alias for the host's loopback |
| Phone over Wi-Fi | The host's LAN IP | Start with `MOCK_VPS_BIND=0.0.0.0`, on a network you trust only, since the server accepts a root password |

The host's LAN IP: `ipconfig getifaddr en0` on macOS, `hostname -I` on Linux, `ipconfig` in a
Windows terminal (the Wi-Fi adapter's IPv4 address, not WSL's). On Windows, allow Docker Desktop
through the firewall when it asks.

`adb reverse` makes the phone's own loopback forward to the host over USB:

| On the phone | Reaches |
|---|---|
| `127.0.0.1:2222` | the container's `sshd` |
| `127.0.0.1:443` | the container's Xray, once the wizard has deployed it |

Both are needed because the app uses one address for SSH and for the VPN connection, and always
dials the VPN on 443 (`XrayServerConfigFactory.DEFAULT_SERVER_PORT`). The VPN excludes the app's
own traffic from the tunnel, so the app's connection to its own loopback stays out of it. After
replugging the phone, run `mock-vps.sh reverse`.

### Ports

The container publishes SSH on host port `2222` and Xray on host port `443`. If the host's 443 is
taken, or Docker cannot bind it (rootless Docker), set `MOCK_VPS_XRAY_PORT` to a free port such as
`8443`. Only `adb reverse` maps it back to 443 on the device, so this works over USB only: the
emulator's `10.0.2.2` and Wi-Fi need the host's own 443.

## What it exercises

- SSH login as root with a password, the trust-on-first-use prompt, and the changed-key warning.
- The exec channel and the SFTP upload, as `SshjConnection` uses them.
- The `apt` dependency install.
- The architecture detection. The container has the host's CPU architecture: an arm64 host (Apple
  Silicon, ARM Linux) installs the `arm64-v8a` Xray asset, an x86_64 host installs `64`. To try the
  other one, start with `DOCKER_DEFAULT_PLATFORM=linux/amd64` (or `linux/arm64`), which is emulated
  and slow.
- Installing and starting the `xray` unit through `systemctl`.
- The protection step: sshguard and unattended-upgrades. Three wrong passwords from the device
  block it for 2 minutes. To unblock: `docker exec kaihi-mock-vps nft flush set ip sshguard attackers`.
- The VPN itself, end to end: device, host, container Xray, internet.

It does not exercise the dnf, yum or pacman paths, or the Alpine/OpenRC service path.

## Limits to know about

**It is not offline.** The container needs the internet. The deploy downloads Xray-core and
RealiTLScanner from GitHub (SHA-256 pinned in the app, so they cannot be swapped for fakes) and
calls `api.ipify.org` and `ipinfo.io`, all from your public IP.

**The masking-domain step can fail from a home connection.** `ScanSniCommand` scans the /24 around
the server's public IP for TLS hosts. Behind your home NAT that is your ISP's neighbourhood, which
may contain nothing usable, and the wizard then stops with "No suitable REALITY SNI found near this
VPS". That is a property of where the container sits, not a bug in the harness. A throwaway VPS
does not have the problem.

## Host keys

The host keys live on a Docker volume, so recreating the container (`down`, then `up`) keeps the
same fingerprint and the app's pin stays valid. `reset` discards the volume. The next `up` then
presents a new key, and the app, which pinned the old one, shows its "host key changed" warning
until you clear its data. That is a quick way to test that flow on purpose.

## Debugging a failed deployment step

The failing step's command output is in the wizard's terminal view. The step names map to scripts:

| Wizard step | Script |
|---|---|
| installing the runtime | `InstallXrayRuntimeCommand` |
| "finding cover for your traffic" | `ScanSniCommand`, then `VerifySniCandidateCommand` |
| generating secrets | `GenerateXrayServerSecretsCommand` |
| starting the service | `StartXrayServiceCommand` |

To reproduce a step by hand, open `mock-vps.sh shell` and paste the script body from the matching
file under `feature/wizard/impl/domain/ssh/`. Run it with `bash -x` to see each line as it executes.
The app runs these as `bash -c '<script>'`, so a script that works in an interactive shell can still
differ in environment.

Reading the exit code:

| Code | Meaning |
|---|---|
| 126 | found but could not be executed: a `noexec` mount, a binary for the wrong architecture, or a missing execute bit |
| 127 | command not found |
| 1 | the script's own failure, such as a SHA-256 mismatch, or `set -eu` tripping on a failed command |

Exit 126 on the scanner step means the download and its checksum already passed: only running the
binary failed. Check `grep noexec /proc/mounts`. A tmpfs on `/tmp` is `noexec` by default, which is
why the compose file does not mount one. Some hardened real VPS images mount `/tmp` the same way.

## Commands

| Command | Does |
|---|---|
| `up` | build, start, set the root password, create the `adb reverse` forwards, print the wizard values |
| `down` | remove the container and the forwards (keeps host keys) |
| `reset` | `down`, and forget the host keys |
| `reverse` | (re)create only the `adb reverse` forwards, for example after replugging the phone |
| `status` | container, `xray` service and forward state |
| `password` | print the root password |
| `fingerprints` | print the SSH host key fingerprints |
| `shell` | root shell in the container |
| `logs` | follow the `xray` journal |

Environment:

| Variable | Default | Use |
|---|---|---|
| `MOCK_VPS_SSH_PORT` | `2222` | host port for SSH |
| `MOCK_VPS_XRAY_PORT` | `443` | host port for Xray, see [Ports](#ports) |
| `MOCK_VPS_BIND` | `127.0.0.1` | host address the ports listen on; `0.0.0.0` for Wi-Fi |
| `ADB` | `adb` | the adb binary; `adb.exe` on WSL |
| `ANDROID_SERIAL` | | which device, when several are attached |

## Security notes

- Ports are bound to the host's loopback by default. The root password is random per machine,
  written to the gitignored `.root-password`, and set from outside the image, never baked into it.
- The container gets `SYS_ADMIN`, a writable cgroup mount and no AppArmor profile, which is what
  systemd as PID 1 needs. It is not `--privileged`. Treat it as disposable and do not put anything
  you care about in it.
