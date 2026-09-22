#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
OPS_DIR="$SCRIPT_DIR/.."
BACKUP="$OPS_DIR/backup-database.sh"
SERVICE="$OPS_DIR/stationery-member-backup.service"
TIMER="$OPS_DIR/stationery-member-backup.timer"
INSTALLER="$OPS_DIR/install-backup.sh"

assert_contains() {
  local file=$1
  local value=$2
  test -f "$file"
  grep -Fq -- "$value" "$file" || {
    echo "$file is missing: $value" >&2
    exit 1
  }
}

assert_contains "$BACKUP" '--defaults-extra-file=/mnt/datadisk0/stationery-member/config/mysql-backup.cnf'
assert_contains "$BACKUP" '--single-transaction'
assert_contains "$BACKUP" '--routines'
assert_contains "$BACKUP" '--triggers'
assert_contains "$BACKUP" '--events'
assert_contains "$BACKUP" '--hex-blob'
assert_contains "$BACKUP" 'gzip -t'
assert_contains "$BACKUP" 'sha256sum'
assert_contains "$BACKUP" "-name 'stationery_member-*.sql.gz'"
assert_contains "$BACKUP" '-mtime +14'

assert_contains "$SERVICE" 'Type=oneshot'
assert_contains "$SERVICE" 'ExecStart=/mnt/datadisk0/stationery-member/config/scripts/backup-database.sh'
assert_contains "$TIMER" 'OnCalendar=daily'
assert_contains "$TIMER" 'Persistent=true'
# Exact source-code contract; expansion is intentionally disabled.
# shellcheck disable=SC2016
assert_contains "$INSTALLER" 'chmod 600 "$CREDENTIAL_FILE"'
assert_contains "$INSTALLER" 'systemctl daemon-reload'

echo 'backup contract: PASS'
