#!/usr/bin/env bash
set -Eeuo pipefail

readonly DATA_DEVICE=/dev/vdb
readonly DATA_MOUNT=/mnt/datadisk0
readonly APP_ROOT=/mnt/datadisk0/stationery-member
readonly MIN_FREE_BYTES=$((20 * 1024 * 1024 * 1024))
readonly MYSQL_BIND_LINE='/mnt/datadisk0/mysql /var/lib/mysql none bind 0 0'
readonly REDIS_BIND_LINE='/mnt/datadisk0/redis /var/lib/redis none bind 0 0'

fail() {
  echo "bootstrap-host: $*" >&2
  exit 1
}

require_root() {
  [[ ${EUID} -eq 0 ]] || fail 'run this script as root'
}

preflight() {
  [[ -b "$DATA_DEVICE" ]] || fail "$DATA_DEVICE is not a block device"
  [[ -r /etc/os-release ]] || fail '/etc/os-release is missing'

  # shellcheck disable=SC1091
  source /etc/os-release
  [[ ${ID:-} == ubuntu && ${VERSION_ID:-} == "24.04" ]] \
    || fail 'this release requires Ubuntu 24.04'

  local source fstype free_bytes
  source=$(findmnt -n -o SOURCE --mountpoint "$DATA_MOUNT") \
    || fail "$DATA_MOUNT is not mounted"
  fstype=$(findmnt -n -o FSTYPE --mountpoint "$DATA_MOUNT")
  [[ $source == "$DATA_DEVICE" ]] \
    || fail "$DATA_MOUNT must be mounted from $DATA_DEVICE, found $source"
  [[ $fstype == ext4 ]] || fail "$DATA_MOUNT must use ext4, found $fstype"

  free_bytes=$(df --output=avail -B1 "$DATA_MOUNT" | tail -n 1 | tr -d ' ')
  [[ $free_bytes =~ ^[0-9]+$ && $free_bytes -ge $MIN_FREE_BYTES ]] \
    || fail "$DATA_MOUNT has less than 20 GB free"
}

install_packages() {
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y \
    openjdk-17-jre-headless \
    mysql-server \
    redis-server \
    nginx \
    certbot \
    python3-certbot-nginx \
    rsync \
    curl \
    unzip \
    ufw \
    shellcheck
}

ensure_service_user() {
  if ! getent passwd stationery >/dev/null; then
    useradd --system --home-dir "$APP_ROOT" --shell /usr/sbin/nologin stationery
  fi
}

create_data_tree() {
  install -d -o stationery -g stationery -m 0750 \
    "$APP_ROOT" \
    "$APP_ROOT/backend" \
    "$APP_ROOT/frontend" \
    "$APP_ROOT/releases" \
    "$APP_ROOT/uploads" \
    "$APP_ROOT/uploads/images"

  install -d -o root -g stationery -m 0750 \
    "$APP_ROOT/config" \
    "$APP_ROOT/config/scripts" \
    "$APP_ROOT/config/wechat-pay" \
    "$APP_ROOT/backups" \
    "$APP_ROOT/backups/mysql" \
    "$APP_ROOT/backups/migration" \
    "$APP_ROOT/backups/legacy-mini" \
    "$APP_ROOT/evidence"
}

ensure_fstab_line() {
  local line=$1
  if ! grep -Fqx -- "$line" /etc/fstab; then
    printf '%s\n' "$line" >>/etc/fstab
  fi
}

relocate_state() {
  local service=$1
  local runtime_dir=$2
  local data_dir=$3
  local owner=$4
  local fstab_line=$5
  local mounted_source

  systemctl stop "$service"
  install -d -o "$owner" -g "$owner" -m 0750 "$data_dir"

  if mountpoint -q "$runtime_dir"; then
    mounted_source=$(findmnt -n -o SOURCE --mountpoint "$runtime_dir")
    case "$mounted_source" in
      "$DATA_DEVICE"|"$DATA_DEVICE"\[*\]) ;;
      *) fail "$runtime_dir is already mounted from unexpected source $mounted_source" ;;
    esac
  else
    rsync -aHAX --numeric-ids "$runtime_dir/" "$data_dir/"
    ensure_fstab_line "$fstab_line"
    mount "$runtime_dir"
  fi

  ensure_fstab_line "$fstab_line"
  chown -R "$owner:$owner" "$data_dir"
  systemctl enable --now "$service"
}

main() {
  require_root
  preflight
  install_packages
  ensure_service_user
  create_data_tree
  relocate_state mysql /var/lib/mysql /mnt/datadisk0/mysql mysql "$MYSQL_BIND_LINE"
  relocate_state redis-server /var/lib/redis /mnt/datadisk0/redis redis "$REDIS_BIND_LINE"
  systemctl enable --now nginx
  echo 'bootstrap-host: complete'
}

main "$@"
