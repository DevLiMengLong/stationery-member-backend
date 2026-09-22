#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
TARGET="$SCRIPT_DIR/../bootstrap-host.sh"

test -f "$TARGET"

assert_contains() {
  local pattern=$1
  if ! grep -Fq -- "$pattern" "$TARGET"; then
    echo "missing required bootstrap contract: $pattern" >&2
    exit 1
  fi
}

assert_contains '/dev/vdb'
assert_contains '/mnt/datadisk0'
assert_contains '== "24.04"'
# Exact source-code contract; expansion is intentionally disabled.
# shellcheck disable=SC2016
assert_contains 'MIN_FREE_BYTES=$((20 * 1024 * 1024 * 1024))'
assert_contains 'openjdk-17-jre-headless'
assert_contains 'mysql-server'
assert_contains 'redis-server'
assert_contains 'nginx'
assert_contains 'useradd --system'
assert_contains '/mnt/datadisk0/mysql /var/lib/mysql none bind 0 0'
assert_contains '/mnt/datadisk0/redis /var/lib/redis none bind 0 0'
assert_contains 'rsync -aHAX'

echo 'bootstrap-host contract: PASS'
