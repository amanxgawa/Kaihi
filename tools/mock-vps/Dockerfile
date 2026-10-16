# A throwaway Debian "VPS" for exercising Kaihi's setup wizard. See README.md.
FROM debian:bookworm-slim

ENV container=docker \
    DEBIAN_FRONTEND=noninteractive

# Deliberately as bare as a fresh VPS image: curl, unzip, openssl and ca-certificates are NOT
# installed here, because the wizard's InstallXrayRuntimeCommand installs them itself and that step
# should be exercised rather than skipped. systemd is PID 1 because StartXrayServiceCommand drives
# the service through systemctl.
RUN apt-get update \
    && apt-get install -y --no-install-recommends systemd systemd-sysv openssh-server \
    && rm -rf /var/lib/apt/lists/* \
    # Host keys live on a volume instead (see mock-vps-hostkeys), so the fingerprint the wizard pins
    # survives the container being recreated.
    && rm -f /etc/ssh/ssh_host_*_key /etc/ssh/ssh_host_*_key.pub \
    # Units that only fail noisily without real hardware or a console.
    && for unit in \
        getty.target console-getty.service \
        systemd-udevd.service systemd-udevd-kernel.socket systemd-udevd-control.socket \
        systemd-udev-trigger.service systemd-modules-load.service; do \
        ln -sf /dev/null "/etc/systemd/system/$unit"; \
    done

COPY rootfs/ /
RUN chmod 0755 /usr/local/sbin/mock-vps-hostkeys

# systemd shuts down cleanly on SIGRTMIN+3, not on the SIGTERM docker sends by default.
STOPSIGNAL SIGRTMIN+3
EXPOSE 22 443
CMD ["/sbin/init"]
