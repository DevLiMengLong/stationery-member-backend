#!/usr/bin/env bash
set -Eeuo pipefail

readonly APP_ROOT=/mnt/datadisk0/stationery-member
readonly ENV_FILE="$APP_ROOT/config/backend.env"
readonly UNIT_FILE=/etc/systemd/system/stationery-member-backend.service
readonly NGINX_AVAILABLE=/etc/nginx/sites-available/stationery-member.conf
readonly NGINX_ENABLED=/etc/nginx/sites-enabled/stationery-member.conf

fail() {
  echo "install-runtime-config: $*" >&2
  exit 1
}

require_root() {
  [[ ${EUID} -eq 0 ]] || fail 'run this script as root'
  [[ -d $APP_ROOT/config ]] || fail "$APP_ROOT/config does not exist; run bootstrap-host.sh first"
  getent passwd stationery >/dev/null || fail 'stationery user does not exist'
}

read_existing_password() {
  [[ -f $ENV_FILE ]] || return 0
  sed -n 's/^DB_PASSWORD=\([[:alnum:]]\+\)$/\1/p' "$ENV_FILE" | head -n 1
}

write_environment() {
  local password=$1
  local temporary
  temporary=$(mktemp "$APP_ROOT/config/backend.env.XXXXXX")
  chmod 600 "$temporary"

  {
    printf 'DB_HOST=127.0.0.1\n'
    printf 'DB_PORT=3306\n'
    printf 'DB_NAME=stationery_member\n'
    printf 'DB_USER=stationery_app\n'
    printf 'DB_PASSWORD=%s\n' "$password"
    printf 'SERVER_ADDRESS=127.0.0.1\n'
    printf 'SERVER_PORT=8090\n'
    printf 'SPRING_PROFILES_ACTIVE=prod\n'
    printf 'MEMBER_NOTIFICATION_CHANNEL=log\n'
    printf 'STATIONERY_PAYMENT_MODE=BOOKKEEPING\n'
    printf 'WECHAT_MINI_APP_ID=\n'
    printf 'WECHAT_MINI_APP_SECRET=\n'
    printf 'WECHAT_PAY_SP_APP_ID=\n'
    printf 'WECHAT_PAY_SP_MCHID=\n'
    printf 'WECHAT_PAY_MERCHANT_SERIAL_NUMBER=\n'
    printf 'WECHAT_PAY_PRIVATE_KEY_PATH=\n'
    printf 'WECHAT_PAY_API_V3_KEY=\n'
    printf 'WECHAT_PAY_PUBLIC_KEY_ID=\n'
    printf 'WECHAT_PAY_PUBLIC_KEY_PATH=\n'
    printf 'WECHAT_PAY_NOTIFY_URL=\n'
  } >"$temporary"

  chown root:stationery "$temporary"
  chmod 640 "$temporary"
  mv -f "$temporary" "$ENV_FILE"
  chmod 640 "$ENV_FILE"
}

configure_database() {
  local password=$1
  mysql --protocol=socket --user=root <<SQL
CREATE DATABASE IF NOT EXISTS stationery_member CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'stationery_app'@'localhost' IDENTIFIED BY '${password}';
CREATE USER IF NOT EXISTS 'stationery_app'@'127.0.0.1' IDENTIFIED BY '${password}';
ALTER USER 'stationery_app'@'localhost' IDENTIFIED BY '${password}';
ALTER USER 'stationery_app'@'127.0.0.1' IDENTIFIED BY '${password}';
GRANT ALL PRIVILEGES ON stationery_member.* TO 'stationery_app'@'localhost';
GRANT ALL PRIVILEGES ON stationery_member.* TO 'stationery_app'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
}

install_service_files() {
  local script_dir
  script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)

  install -o root -g root -m 0644 \
    "$script_dir/stationery-member-backend.service" "$UNIT_FILE"
  install -o root -g root -m 0644 \
    "$script_dir/stationery-member.nginx.conf" "$NGINX_AVAILABLE"
  ln -sfn "$NGINX_AVAILABLE" "$NGINX_ENABLED"
  rm -f /etc/nginx/sites-enabled/default

  systemctl daemon-reload
  systemctl enable stationery-member-backend.service
  nginx -t
}

main() {
  local password
  require_root
  password=$(read_existing_password)
  if [[ -z $password ]]; then
    password=$(openssl rand -hex 24)
  fi

  configure_database "$password"
  write_environment "$password"
  install_service_files
  echo 'install-runtime-config: complete (backend remains stopped until a release is staged)'
}

main "$@"
