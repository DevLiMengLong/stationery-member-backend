#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
OPS_DIR="$SCRIPT_DIR/.."
SNAPSHOT="$OPS_DIR/capture-business-snapshot.sql"
PREPARE="$OPS_DIR/prepare-import.sql"
POST="$OPS_DIR/post-import.sql"

assert_contains() {
  local file=$1
  local value=$2
  test -f "$file"
  grep -Fq -- "$value" "$file" || {
    echo "$file is missing: $value" >&2
    exit 1
  }
}

for table in admin_user audit_log login_session member_wallet recharge_tier store_account store_member uploaded_file verification_code wallet_transaction; do
  assert_contains "$SNAPSHOT" "count.$table"
done
for amount in recharge_balance gift_balance accumulated_recharge accumulated_gift accumulated_consumption amount gift_amount; do
  assert_contains "$SNAPSHOT" "$amount"
done
assert_contains "$SNAPSHOT" 'ORDER BY metric_key'

assert_contains "$PREPARE" 'DROP DATABASE IF EXISTS stationery_member'
assert_contains "$PREPARE" 'CREATE DATABASE stationery_member CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci'

assert_contains "$POST" 'START TRANSACTION'
assert_contains "$POST" 'DELETE FROM login_session'
assert_contains "$POST" 'DELETE FROM verification_code'
assert_contains "$POST" 'COMMIT'

echo 'migration SQL contract: PASS'
