#!/usr/bin/env bash
set -Eeuo pipefail

readonly APP_ROOT=/mnt/datadisk0/stationery-member
readonly CREDENTIAL_FILE="$APP_ROOT/config/mysql-backup.cnf"
readonly SCRIPT_PATH="$APP_ROOT/config/scripts/backup-database.sh"

[[ ${EUID} -eq 0 ]] || { echo 'install-backup: run as root' >&2; exit 1; }
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)

install -d -o root -g stationery -m 0750 "$APP_ROOT/config/scripts" "$APP_ROOT/backups/mysql"
credential_temp=$(mktemp "$APP_ROOT/config/mysql-backup.cnf.XXXXXX")
trap 'rm -f "$credential_temp"' EXIT
printf '[client]\nuser=root\nprotocol=socket\n' > "$credential_temp"
chown root:root "$credential_temp"
chmod 600 "$credential_temp"
mv "$credential_temp" "$CREDENTIAL_FILE"
trap - EXIT
chmod 600 "$CREDENTIAL_FILE"

install -o root -g stationery -m 0750 "$script_dir/backup-database.sh" "$SCRIPT_PATH"
install -o root -g root -m 0644 \
  "$script_dir/stationery-member-backup.service" \
  /etc/systemd/system/stationery-member-backup.service
install -o root -g root -m 0644 \
  "$script_dir/stationery-member-backup.timer" \
  /etc/systemd/system/stationery-member-backup.timer
systemctl daemon-reload

echo 'install-backup: complete (timer remains disabled until restore validation passes)'
