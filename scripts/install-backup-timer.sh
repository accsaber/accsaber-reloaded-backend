#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_USER="${SUDO_USER:-$(id -un)}"
OUT_DIR="${OUT_DIR:-$(getent passwd "$RUN_USER" | cut -d: -f6)/accsaber-backups}"
AT="${AT:-03:00:00}"

if [[ ! -x "$REPO_DIR/scripts/backup-db.sh" ]]; then
  echo "error: $REPO_DIR/scripts/backup-db.sh is not executable" >&2
  echo "run: chmod +x $REPO_DIR/scripts/*.sh" >&2
  exit 1
fi

if ! id -nG "$RUN_USER" | tr ' ' '\n' | grep -qx docker; then
  echo "error: user '$RUN_USER' is not in the docker group; the timer would fail" >&2
  exit 1
fi

echo "installing timer:"
echo "  user    : $RUN_USER"
echo "  script  : $REPO_DIR/scripts/backup-db.sh"
echo "  output  : $OUT_DIR"
echo "  runs    : daily at $AT UTC"
echo

sudo tee /etc/systemd/system/accsaber-backup.service > /dev/null <<EOF
[Unit]
Description=AccSaber production database backup
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
User=$RUN_USER
Environment=OUT_DIR=$OUT_DIR
ExecStart=$REPO_DIR/scripts/backup-db.sh
Nice=10
IOSchedulingClass=idle
TimeoutStartSec=3600
EOF

sudo tee /etc/systemd/system/accsaber-backup.timer > /dev/null <<EOF
[Unit]
Description=Daily AccSaber production database backup

[Timer]
OnCalendar=*-*-* $AT UTC
Persistent=true
RandomizedDelaySec=300

[Install]
WantedBy=timers.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now accsaber-backup.timer

echo
sudo systemctl list-timers accsaber-backup.timer --no-pager
echo
echo "run:"
echo "  sudo systemctl start --no-block accsaber-backup.service && journalctl -u accsaber-backup -f"
