#!/usr/bin/env bash
set -Eeuo pipefail

readonly APP_ROOT=/mnt/datadisk0/stationery-member
readonly BACKUP_DIR="$APP_ROOT/backups/mysql"
readonly MYSQL_CONFIG="$APP_ROOT/config/mysql-backup.cnf"
STAMP=$(date +%Y%m%d%H%M%S)
readonly STAMP
readonly FINAL_NAME="stationery_member-$STAMP.sql.gz"
readonly FINAL_PATH="$BACKUP_DIR/$FINAL_NAME"
readonly CHECKSUM_PATH="$FINAL_PATH.sha256"
TEMP_PATH=''
TEMP_CHECKSUM=''

cleanup() {
  [[ -z $TEMP_PATH ]] || rm -f -- "$TEMP_PATH"
  [[ -z $TEMP_CHECKSUM ]] || rm -f -- "$TEMP_CHECKSUM"
}
trap cleanup EXIT

[[ ${EUID} -eq 0 ]] || { echo 'backup-database: run as root' >&2; exit 1; }
[[ -r $MYSQL_CONFIG ]] || { echo 'backup-database: credential file is missing' >&2; exit 1; }
install -d -o root -g stationery -m 0750 "$BACKUP_DIR"

TEMP_PATH=$(mktemp "$BACKUP_DIR/.stationery_member-$STAMP.XXXXXX.sql.gz.tmp")
chmod 600 "$TEMP_PATH"
mysqldump \
  --defaults-extra-file=/mnt/datadisk0/stationery-member/config/mysql-backup.cnf \
  --single-transaction \
  --routines \
  --triggers \
  --events \
  --hex-blob \
  --set-gtid-purged=OFF \
  --default-character-set=utf8mb4 \
  stationery_member | gzip -9 > "$TEMP_PATH"
gzip -t "$TEMP_PATH"
mv "$TEMP_PATH" "$FINAL_PATH"
TEMP_PATH=''

TEMP_CHECKSUM="$CHECKSUM_PATH.tmp"
(cd "$BACKUP_DIR" && sha256sum "$FINAL_NAME" > "$(basename "$TEMP_CHECKSUM")")
chmod 600 "$TEMP_CHECKSUM"
mv "$TEMP_CHECKSUM" "$CHECKSUM_PATH"
TEMP_CHECKSUM=''
chown root:root "$FINAL_PATH" "$CHECKSUM_PATH"
chmod 600 "$FINAL_PATH" "$CHECKSUM_PATH"

while IFS= read -r -d '' old_dump; do
  old_checksum="$old_dump.sha256"
  if [[ -f $old_checksum ]]; then
    rm -f -- "$old_dump" "$old_checksum"
  fi
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'stationery_member-*.sql.gz' -mtime +14 -print0)

echo "backup-database: created $FINAL_NAME"
