# Mock VPS

A throwaway Debian container that stands in for the VPS the setup wizard deploys to, so you can run
the wizard on a USB-connected phone without renting a server. It is a real Linux userland with
systemd, `sshd` and `apt`, not a stub: the wizard's scripts run against it unmodified.

Needs Docker and `adb`. Nothing here is built into the app.

## Quick start

```sh
tools/mock-vps/mock-vps.sh up        # build, start, set the root password, forward ports over USB
tools/mock-vps/mock-vps.sh password  # the root password for the wizard
```

`up` prints the values to type into the wizard:

| Field | Value |
|---|---|
| Address | `127.0.0.1` (emulator: `10.0.2.2`; over Wi-Fi: the Mac's LAN IP, see below) |
| SSH port | `2222` |
| Username | `root` |
| Password | `mock-vps.sh password` |

It also prints the host key fingerprints. The wizard's trust-on-first-use prompt should show one of
them, in the same `SHA256:` format `ssh-keygen` uses, so you can check that the prompt is about this
container.

## How the phone reaches it

`up` runs `adb reverse`, which makes the phone's own loopback forward to the Mac over USB:

| On the phone | Reaches |
|---|---|
| `127.0.0.1:2222` | the container's `sshd` |
| `127.0.0.1:443` | the container's Xray, once the wizard has deployed it |

Both are needed because the app uses one address for SSH and for the VPN connection, and always
dials the VPN on 443 (`XrayServerConfigFactory.DEFAULT_SERVER_PORT`). The VPN excludes the app's
own traffic from the tunnel, so the app's connection to its own loopback stays out of it.

Without USB: on an emulator use `10.0.2.2` and skip the forwards. Over Wi-Fi start with
`MOCK_VPS_BIND=0.0.0.0` and use the Mac's LAN IP, but only on a network you trust, since the server
accepts a root password.

## What it exercises

- SSH login as root with a password, the trust-on-first-use prompt, and the changed-key warning.
- The exec channel and the SFTP upload, as `SshjConnection` uses them.
- The `apt` dependency install, and `uname -m` as `aarch64` on Apple Silicon (the `arm64-v8a` asset).
- Installing and starting the `kaihi-xray` unit through `systemctl`.
- The VPN itself, end to end: phone, USB, Mac, container Xray, internet.

It does not exercise the dnf, yum or pacman paths, or the Alpine/OpenRC service path. For an
`x86_64` server, run with `DOCKER_DEFAULT_PLATFORM=linux/amd64`, which is emulated and slow.

## Limits to know about

**It is not offline.** The container needs the internet. The deploy downloads Xray-core and
RealiTLScanner from GitHub (SHA-256 pinned in the app, so they cannot be swapped for fakes) and
calls `api.ipify.org` and `ipinfo.io`, all from your Mac's public IP.

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
| `status` | container, `kaihi-xray` service and forward state |
| `password` | print the root password |
| `fingerprints` | print the SSH host key fingerprints |
| `shell` | root shell in the container |
| `logs` | follow the `kaihi-xray` journal |

Environment: `MOCK_VPS_SSH_PORT` (default `2222`), `MOCK_VPS_XRAY_PORT` (default `443`, the host
port, so set another if the Mac's 443 is busy and the phone still sees 443), `MOCK_VPS_BIND`
(default `127.0.0.1`). With several devices attached, set `ANDROID_SERIAL`.

## Security notes

- Ports are bound to the Mac's loopback by default. The root password is random per machine, written
  to the gitignored `.root-password`, and set from outside the image, never baked into it.
- The container gets `SYS_ADMIN`, a writable cgroup mount and no AppArmor profile, which is what
  systemd as PID 1 needs. It is not `--privileged`. Treat it as disposable and do not put anything
  you care about in it.
