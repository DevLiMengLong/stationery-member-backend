#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
OPS_DIR="$SCRIPT_DIR/.."
UNIT="$OPS_DIR/stationery-member-backend.service"
NGINX="$OPS_DIR/stationery-member.nginx.conf"
INSTALLER="$OPS_DIR/install-runtime-config.sh"

assert_file_contains() {
  local file=$1
  local value=$2
  test -f "$file"
  grep -Fq -- "$value" "$file" || {
    echo "$file is missing: $value" >&2
    exit 1
  }
}

assert_file_contains "$UNIT" 'User=stationery'
assert_file_contains "$UNIT" 'EnvironmentFile=/mnt/datadisk0/stationery-member/config/backend.env'
assert_file_contains "$UNIT" 'ExecStart=/usr/bin/java -XX:MaxRAMPercentage=60 -jar /mnt/datadisk0/stationery-member/backend/current.jar'
assert_file_contains "$UNIT" 'ProtectSystem=strict'
assert_file_contains "$UNIT" 'ReadWritePaths=/mnt/datadisk0/stationery-member/uploads'

assert_file_contains "$NGINX" 'listen 80 default_server;'
assert_file_contains "$NGINX" 'root /mnt/datadisk0/stationery-member/frontend/current;'
assert_file_contains "$NGINX" 'proxy_pass http://127.0.0.1:8090;'
assert_file_contains "$NGINX" 'alias /mnt/datadisk0/stationery-member/uploads/;'
assert_file_contains "$NGINX" 'client_max_body_size 2m;'
# Exact Nginx contract; expansion is intentionally disabled.
# shellcheck disable=SC2016
assert_file_contains "$NGINX" 'try_files $uri $uri/ /index.html;'

assert_file_contains "$INSTALLER" 'STATIONERY_PAYMENT_MODE=BOOKKEEPING'
assert_file_contains "$INSTALLER" 'SERVER_ADDRESS=127.0.0.1'
assert_file_contains "$INSTALLER" 'usermod --append --groups stationery www-data'
assert_file_contains "$INSTALLER" "CREATE USER IF NOT EXISTS 'stationery_app'@'localhost'"
assert_file_contains "$INSTALLER" "CREATE USER IF NOT EXISTS 'stationery_app'@'127.0.0.1'"
# Exact source-code contract; expansion is intentionally disabled.
# shellcheck disable=SC2016
assert_file_contains "$INSTALLER" 'chmod 640 "$ENV_FILE"'

echo 'runtime-config contract: PASS'
