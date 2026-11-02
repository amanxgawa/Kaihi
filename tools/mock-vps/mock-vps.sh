#!/usr/bin/env bash
# Control script for the Kaihi mock VPS. See README.md.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

CONTAINER=kaihi-mock-vps
PASSWORD_FILE=.root-password

export MOCK_VPS_BIND="${MOCK_VPS_BIND:-127.0.0.1}"
export MOCK_VPS_SSH_PORT="${MOCK_VPS_SSH_PORT:-2222}"
export MOCK_VPS_XRAY_PORT="${MOCK_VPS_XRAY_PORT:-443}"

# The app always dials the server on 443 (XrayServerConfigFactory.DEFAULT_SERVER_PORT), so that is
# the port that has to exist on the phone, whichever host port the container answers on.
DEVICE_XRAY_PORT=443

die() { echo "error: $*" >&2; exit 1; }

need_docker() {
  command -v docker >/dev/null 2>&1 || die "docker not found on PATH"
  docker info >/dev/null 2>&1 || die "the Docker daemon is not running (start Docker Desktop)"
}

# Random per machine, kept out of git, and set from outside the image so it is never baked in.
root_password() {
  if [ ! -s "$PASSWORD_FILE" ]; then
    (umask 077; head -c 64 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | cut -c1-20 >"$PASSWORD_FILE")
  fi
  cat "$PASSWORD_FILE"
}

in_container() { docker exec "$CONTAINER" "$@"; }
in_container_stdin() { docker exec -i "$CONTAINER" "$@"; }

wait_for_systemd() {
  local state
  for _ in $(seq 1 60); do
    state="$(in_container systemctl is-system-running 2>/dev/null || true)"
    case "$state" in running | degraded) return 0 ;; esac
    sleep 1
  done
  die "systemd did not finish booting in the container (see: docker logs $CONTAINER)"
}

wait_for_ssh() {
  for _ in $(seq 1 30); do
    in_container systemctl is-active --quiet ssh && return 0
    sleep 1
  done
  die "sshd did not start (see: ./mock-vps.sh shell, then journalctl -u ssh)"
}

fingerprints() {
  in_container sh -c 'for f in /var/lib/mock-vps/hostkeys/*.pub; do ssh-keygen -lf "$f"; done'
}

cmd_reverse() {
  command -v adb >/dev/null 2>&1 || die "adb not found on PATH"
  adb get-state >/dev/null 2>&1 || die "no single adb device (check 'adb devices', or set ANDROID_SERIAL)"
  # Explicit returns: `set -e` is off inside a function that cmd_up calls from an `if`.
  adb reverse "tcp:${MOCK_VPS_SSH_PORT}" "tcp:${MOCK_VPS_SSH_PORT}" || return 1
  adb reverse "tcp:${DEVICE_XRAY_PORT}" "tcp:${MOCK_VPS_XRAY_PORT}" || return 1
  echo "adb reverse: phone 127.0.0.1:${MOCK_VPS_SSH_PORT} -> Mac :${MOCK_VPS_SSH_PORT}, phone 127.0.0.1:${DEVICE_XRAY_PORT} -> Mac :${MOCK_VPS_XRAY_PORT}"
}

remove_reverse() {
  command -v adb >/dev/null 2>&1 || return 0
  adb reverse --remove "tcp:${MOCK_VPS_SSH_PORT}" >/dev/null 2>&1 || true
  adb reverse --remove "tcp:${DEVICE_XRAY_PORT}" >/dev/null 2>&1 || true
}

cmd_up() {
  need_docker
  docker compose up -d --build
  wait_for_systemd
  root_password | sed 's/^/root:/' | in_container_stdin chpasswd
  wait_for_ssh

  if ! cmd_reverse; then
    echo "warning: the USB forwards were not set up; fix adb and run './mock-vps.sh reverse'," >&2
    echo "         or reach the host some other way (emulator: 10.0.2.2, Wi-Fi: MOCK_VPS_BIND=0.0.0.0)." >&2
  fi
  if [ "$MOCK_VPS_BIND" != "127.0.0.1" ]; then
    echo "warning: listening on ${MOCK_VPS_BIND}, so anything on that network can try the root password." >&2
  fi

  cat <<EOF

Mock VPS is up. In the wizard:
  Address   127.0.0.1  (emulator: 10.0.2.2, Wi-Fi: this Mac's LAN IP)
  SSH port  ${MOCK_VPS_SSH_PORT}
  Username  root
  Password  run: ./mock-vps.sh password
  Host key  the wizard should show one of these (the same SHA256 format ssh-keygen prints):
EOF
  fingerprints | sed 's/^/            /'
}

cmd_down() {
  need_docker
  remove_reverse
  docker compose down
}

cmd_reset() {
  need_docker
  remove_reverse
  docker compose down --volumes
  echo "Host keys discarded: the next 'up' presents a new fingerprint."
  echo "Clear the app's data too, or it will answer with its 'host key changed' warning."
}

cmd_status() {
  need_docker
  docker compose ps
  if in_container true >/dev/null 2>&1; then
    echo "--- kaihi-xray service"
    in_container systemctl is-active kaihi-xray.service 2>&1 || true
  fi
  command -v adb >/dev/null 2>&1 && { echo "--- adb reverse"; adb reverse --list 2>&1 || true; }
}

usage() {
  cat <<EOF
usage: ./mock-vps.sh <command>

  up            build, start, set the root password, forward ports over USB, print the wizard values
  down          stop and remove the container and the adb forwards (host keys are kept)
  reset         down, and also forget the host keys (new fingerprint on the next up)
  reverse       (re)create just the adb reverse forwards
  status        container, kaihi-xray service and forward state
  password      print the root password
  fingerprints  print the SSH host key fingerprints
  shell         open a root shell in the container
  logs          follow the kaihi-xray journal

environment: MOCK_VPS_SSH_PORT (2222), MOCK_VPS_XRAY_PORT (443), MOCK_VPS_BIND (127.0.0.1)
EOF
}

case "${1:-}" in
  up) cmd_up ;;
  down) cmd_down ;;
  reset) cmd_reset ;;
  reverse) cmd_reverse ;;
  status) cmd_status ;;
  password) root_password ;;
  fingerprints) need_docker; fingerprints ;;
  shell) need_docker; docker exec -it "$CONTAINER" bash ;;
  logs) need_docker; docker exec -i "$CONTAINER" journalctl -u kaihi-xray.service -f ;;
  *) usage; [ -z "${1:-}" ] || exit 1 ;;
esac
