# Tencent Deployment and Production Data Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deploy the shared Java/Vue/miniprogram system to Tencent Cloud `101.35.96.147`, place every application and persistent business-data artifact on `/mnt/datadisk0`, and migrate the live Alibaba Cloud `stationery_member` database during a controlled 5-10 minute write outage with a verified rollback path.

**Architecture:** Ubuntu manages packages, systemd units, Nginx sites, and security policy. MySQL and Redis keep their Ubuntu-standard runtime paths but use bind mounts backed by `/mnt/datadisk0`; this preserves package/AppArmor conventions while placing persistent data on the data disk. Versioned application releases, runtime configuration, uploads, payment material, migration evidence, and backups all live below `/mnt/datadisk0/stationery-member`.

**Tech Stack:** Ubuntu 24.04, OpenJDK 17, MySQL 8, Redis 7, Nginx 1.24+, systemd, rsync, mysqldump, UFW.

**Spec:** `docs/superpowers/specs/2026-09-21-tencent-cloud-miniprogram-migration-design.md`

**Dependencies:** Complete the backend implementation plan and miniprogram local verification before production cutover. Payment remains `BOOKKEEPING`; no real merchant credentials are required or accepted for this release.

## Global Constraints

- Do not write application releases, database data, Redis persistence, uploads, runtime secrets, or backups to the Tencent system disk.
- Do not print, copy into evidence, or commit database passwords, AppSecret, access keys, upload keys, API v3 keys, private keys, session tokens, or QR contents.
- Keep the Alibaba Cloud database and uploads intact. Stop only the old Java writer during final cutover and retain it as a stopped rollback source.
- Do not allow both Alibaba and Tencent backends to accept writes at the same time.
- Do not expose MySQL `3306`, Redis `6379`, or Java `8090` publicly.
- Do not enable `WECHAT_SERVICE_PROVIDER` until service-provider credentials, store `sub_mchid`, HTTPS callback, and real-funds test authorization exist.
- Do not claim migration success unless row counts and wallet/transaction monetary aggregates match the source snapshot, excluding intentionally cleared sessions and verification codes.
- Once Tencent accepts a business write, rollback requires reconciling Tencent delta data; never blindly restart Alibaba as if no new writes occurred.

## Target Layout

```text
/mnt/datadisk0/stationery-member/
├── backend/current.jar -> ../releases/<release-id>/backend.jar
├── frontend/current -> ../releases/<release-id>/frontend
├── uploads/images/
├── releases/<release-id>/
├── config/backend.env
├── config/mysql-backup.cnf
├── config/wechat-pay/
├── config/scripts/
├── backups/mysql/
├── backups/migration/
├── backups/legacy-mini/
└── evidence/

/mnt/datadisk0/mysql/
/mnt/datadisk0/redis/
```

## Review Focus

- `/dev/vdb` is mounted at `/mnt/datadisk0` through `/etc/fstab` before any service installation or copy.
- MySQL and Redis bind mounts survive reboot and retain correct ownership.
- Java listens on `127.0.0.1:8090`; Nginx is the only public application listener.
- The imported database is the source production snapshot plus approved incremental schema, not local demo data.
- The cutover has explicit go/no-go checkpoints and a rollback action before and after first Tencent write.
- Every destructive database operation names `stationery_member` explicitly and follows a fresh source backup.

---

### Task 1: Remove exposed local secrets and prepare tracked operations assets

**Files:**
- Modify: `../README.md`
- Modify: `../.gitignore`
- Modify: `src/main/resources/application-local.yml`
- Create: `ops/tencent/backend.env.example`
- Create: `ops/tencent/README.md`
- Create: `../migration-evidence/2026-09-21/README.md`
- Move locally: `../private.wxa33f35432bbd74fe.key` to `../.secrets/wechat/private.wxa33f35432bbd74fe.key`

**Interfaces:**
- Produces: documentation and templates containing variable names only.
- Preserves: the existing upload key locally in an ignored, mode-600 path pending control-plane rotation.

- [ ] **Step 1: Record only secret identifiers that require external rotation**

In the evidence README, list the categories `WECHAT_MINI_APP_SECRET`, WeChat upload key, Alibaba RAM access keys, database password, and any notification-provider keys. Do not copy current values.

- [ ] **Step 2: Redact the root README and correct environment facts**

Remove every plaintext credential and replace it with environment-variable names and rotation instructions. Correct the domain status to “备案未通过” and make Tencent the target environment. Keep the public miniprogram AppID only where required by the project config.

- [ ] **Step 3: Move the upload key into an ignored local secret directory**

```bash
mkdir -p .secrets/wechat
mv private.wxa33f35432bbd74fe.key .secrets/wechat/
chmod 600 .secrets/wechat/private.wxa33f35432bbd74fe.key
```

Add `.secrets/`, `private.*.key`, `migration-evidence/**/*.sql*`, and database dumps to the root ignore file.

- [ ] **Step 4: Replace the ignored local database credential with environment references**

Keep the local profile but use `${LOCAL_DB_URL}`, `${LOCAL_DB_USER}`, and `${LOCAL_DB_PASSWORD}` without defaults. Do not migrate the current local password to another tracked file.

- [ ] **Step 5: Create a production environment template with bookkeeping defaults**

```text
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=stationery_member
DB_USER=stationery_app
DB_PASSWORD=
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8090
SPRING_PROFILES_ACTIVE=prod
MEMBER_NOTIFICATION_CHANNEL=log
STATIONERY_PAYMENT_MODE=BOOKKEEPING
WECHAT_MINI_APP_ID=
WECHAT_MINI_APP_SECRET=
```

Do not add service-provider payment secrets until real onboarding.

- [ ] **Step 6: Run a secret and stale-environment scan**

```bash
rg -n "AppSecret|AccessKey Secret|ALIBABA_CLOUD_ACCESS_KEY_(ID|SECRET)=|DB_PASSWORD=[^$[:space:]]|private\.wx[a-z0-9]+\.key|47\.101\.217\.103.*(已备案|生产)" . -g '!**/node_modules/**' -g '!**/target/**' -g '!**/.secrets/**'
```

Expected: documentation may mention secret variable names, but no secret values or false备案 claim remains.

- [ ] **Step 7: Commit only tracked backend operations assets**

```bash
git add ops/tencent
git commit -m "ops: add tencent deployment plan and templates"
```

Root-level files and the local ignored profile are outside the backend Git repository; record their checksums in evidence instead of pretending they are committed.

### Task 2: Bootstrap Tencent packages, users, and data-disk persistence

**Files:**
- Create: `ops/tencent/bootstrap-host.sh`
- Create on Tencent: `/mnt/datadisk0/stationery-member/**`
- Modify on Tencent: `/etc/fstab`

**Interfaces:**
- Produces: enabled MySQL, Redis, and Nginx services with persistent data backed by `/mnt/datadisk0`.
- Produces: a non-login `stationery` application user.

- [ ] **Step 1: Write an idempotent bootstrap script with preflight guards**

The script must abort unless all conditions pass:

```text
/dev/vdb exists
/mnt/datadisk0 is a mounted ext4 filesystem
findmnt source for /mnt/datadisk0 is /dev/vdb
at least 20 GB is free
the host is Ubuntu 24.04
```

It then installs `openjdk-17-jre-headless`, `mysql-server`, `redis-server`, `nginx`, `rsync`, `curl`, `unzip`, `ufw`, and `shellcheck`, and creates `stationery` as a system user with no login shell.

- [ ] **Step 2: Create the exact data-disk directory tree**

Set application/release/upload ownership to `stationery:stationery`, backup/evidence/config ownership to `root:stationery`, config directory mode to `750`, environment/backup credential files to `640`, and future payment private keys to `640` or stricter.

- [ ] **Step 3: Relocate MySQL persistence with a bind mount**

The script stops MySQL, copies `/var/lib/mysql/` to `/mnt/datadisk0/mysql/` with `rsync -aHAX`, sets `mysql:mysql` ownership, adds this exact idempotent fstab entry, mounts it, and restarts MySQL:

```text
/mnt/datadisk0/mysql /var/lib/mysql none bind 0 0
```

Abort rather than copying if `/var/lib/mysql` is already mounted from an unexpected source.

- [ ] **Step 4: Relocate Redis persistence with a bind mount**

Repeat the guarded stop/copy/ownership/mount/start sequence for:

```text
/mnt/datadisk0/redis /var/lib/redis none bind 0 0
```

- [ ] **Step 5: Execute bootstrap and verify persistence paths**

```bash
rsync -avP ops/tencent/bootstrap-host.sh tencent:/tmp/bootstrap-host.sh
ssh tencent 'sudo bash /tmp/bootstrap-host.sh'
ssh tencent 'findmnt /mnt/datadisk0 /var/lib/mysql /var/lib/redis'
ssh tencent 'sudo systemctl is-active mysql redis-server nginx'
ssh tencent 'java -version; mysql --version; redis-server --version; nginx -v'
```

Expected: all services are active and both state directories show data-disk bind sources.

- [ ] **Step 6: Reboot once before business deployment and recheck**

After confirming SSH key access, reboot Tencent, reconnect, and rerun `findmnt` plus service checks. Do not proceed if either bind mount fell back to the system disk.

### Task 3: Install runtime configuration, systemd, and Nginx

**Files:**
- Create: `ops/tencent/stationery-member-backend.service`
- Create: `ops/tencent/stationery-member.nginx.conf`
- Create: `ops/tencent/install-runtime-config.sh`
- Create on Tencent: `/mnt/datadisk0/stationery-member/config/backend.env`
- Create on Tencent: `/etc/systemd/system/stationery-member-backend.service`
- Create on Tencent: `/etc/nginx/sites-available/stationery-member.conf`

**Interfaces:**
- Produces: local-only Java and public HTTP Nginx routing.
- Preserves: Ubuntu-owned systemd/Nginx configuration locations.

- [ ] **Step 1: Add server binding support to backend configuration if not already present**

Set `server.address: ${SERVER_ADDRESS:127.0.0.1}` in `application.yml`; verify local development remains possible by explicitly overriding when needed.

- [ ] **Step 2: Create the hardened systemd unit**

Use:

```ini
[Service]
User=stationery
Group=stationery
WorkingDirectory=/mnt/datadisk0/stationery-member
EnvironmentFile=/mnt/datadisk0/stationery-member/config/backend.env
ExecStart=/usr/bin/java -XX:MaxRAMPercentage=60 -jar /mnt/datadisk0/stationery-member/backend/current.jar
Restart=on-failure
RestartSec=5
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/mnt/datadisk0/stationery-member/uploads
```

Add sensible startup and stop timeouts. The unit must not contain secret values.

- [ ] **Step 3: Create the pre-备案 IP Nginx site**

Serve `/mnt/datadisk0/stationery-member/frontend/current`, proxy `/api/` to `http://127.0.0.1:8090`, alias `/uploads/` to the data-disk upload directory, limit upload size to 2 MB, set forwarding headers, and use SPA `try_files $uri $uri/ /index.html`. Do not add fake HTTPS or domain activation before备案/certificate readiness.

- [ ] **Step 4: Generate the database password on Tencent without printing it**

Create the `stationery_app` MySQL users for `localhost` and `127.0.0.1`, grant only `stationery_member.*`, and write the same generated value into `backend.env` mode `640`, owner `root:stationery`. Keep `STATIONERY_PAYMENT_MODE=BOOKKEEPING`.

- [ ] **Step 5: Install and validate configuration**

```bash
rsync -avP ops/tencent/ tencent:/tmp/stationery-ops/
ssh tencent 'sudo bash /tmp/stationery-ops/install-runtime-config.sh'
ssh tencent 'sudo systemd-analyze verify /etc/systemd/system/stationery-member-backend.service'
ssh tencent 'sudo nginx -t'
ssh tencent 'sudo ss -lntp'
```

Expected: configuration validates; Java is not started until a release and database are staged.

### Task 4: Build, verify, and stage a versioned release

**Files:**
- Build: `target/stationery-member-backend-0.0.1-SNAPSHOT.jar`
- Build: `../frontend/dist/`
- Verify: `../merchant-miniprogram/`
- Create on Tencent: `/mnt/datadisk0/stationery-member/releases/<release-id>/`

**Interfaces:**
- Produces: immutable backend and frontend artifacts plus checksums on the Tencent data disk.
- Does not publish: a WeChat miniprogram release.

- [ ] **Step 1: Run the focused backend suite and package**

Use the exact suite from the backend implementation plan, then:

```bash
mvn -q -DskipTests package
```

Expected: the executable JAR exists.

- [ ] **Step 2: Verify and build Vue**

```bash
cd ../frontend
npm ci
npm run test:ui-spacing
npm run typecheck
npm run build
```

Expected: tests/typecheck PASS and `dist/` exists.

- [ ] **Step 3: Verify the rebuilt miniprogram**

```bash
cd ../merchant-miniprogram
npm ci
npm test
npm run check
```

Expected: PASS; no upload is performed.

- [ ] **Step 4: Create release checksums and stage artifacts**

Use release ID `YYYYMMDDHHMMSS-<short-backend-commit>`. Upload the JAR and frontend `dist/` into a new release directory, create SHA-256 checksums locally and remotely, and compare them before changing `current.jar` or `current` symlinks.

- [ ] **Step 5: Initialize an empty target database for pre-cutover smoke only**

Import `01-schema.sql`, then the incremental miniprogram/payment migration. Start the service with bookkeeping defaults and verify `GET /api/health`, the Web SPA, and local-only listeners. Stop the service before final production import.

### Task 5: Capture a source snapshot and enter the maintenance window

**Files:**
- Create: `ops/tencent/capture-business-snapshot.sql`
- Create locally: `../migration-evidence/2026-09-21/source-before.json`
- Create on Alibaba: `/opt/stationery-member/backups/stationery_member-<timestamp>.sql.gz`

**Interfaces:**
- Produces: source row counts and monetary aggregates captured after writes stop.
- Produces: a timestamped compressed logical backup.

- [ ] **Step 1: Write the deterministic snapshot query**

Return counts for every business table and grouped monetary aggregates for `member_wallet` and `wallet_transaction`. Include at least sums of recharge balance, gift balance, accumulated recharge/gift/consumption, transaction amount/gift amount grouped by type/status, and maximum IDs/timestamps. Emit machine-readable tab-separated rows in stable key order.

- [ ] **Step 2: Run pre-maintenance readiness checks**

Confirm Tencent health on the empty database, target disk free space, target backup directory, source MySQL health, both SSH aliases, local artifact checksums, and a tested command for restarting Alibaba. Do not start the outage if any check fails.

- [ ] **Step 3: Stop only the Alibaba Java writer and verify quiescence**

```bash
ssh aliyun 'systemctl stop stationery-member-backend && systemctl is-active stationery-member-backend'
ssh aliyun "ss -lntp | grep ':8090' || true"
```

Expected: service is inactive and nothing listens on `8090`. MySQL, Redis, Nginx, and files remain intact.

- [ ] **Step 4: Capture the authoritative source snapshot**

Run `capture-business-snapshot.sql` through the source MySQL socket after the backend is stopped. Store the output locally as `source-before.tsv` and convert only structural results, not credentials, into `source-before.json`.

- [ ] **Step 5: Create the compressed production dump on Alibaba**

Use `mysqldump --single-transaction --routines --triggers --events --hex-blob --set-gtid-purged=OFF --default-character-set=utf8mb4 stationery_member`, compress it, record SHA-256 and byte size, and ensure mode `600`.

- [ ] **Step 6: Apply the first go/no-go checkpoint**

If the source snapshot or dump fails, delete no data, restart the Alibaba backend, end maintenance, and report the failed step. Continue only with a valid checksum-bearing dump.

### Task 6: Transfer, import, upgrade, and validate production data

**Files:**
- Copy to Tencent: `/mnt/datadisk0/stationery-member/backups/migration/stationery_member-<timestamp>.sql.gz`
- Copy to Tencent: `/mnt/datadisk0/stationery-member/backups/migration/uploads-<timestamp>.tar.gz`
- Create: `ops/tencent/prepare-import.sql`
- Create: `ops/tencent/post-import.sql`
- Create locally: `../migration-evidence/2026-09-21/target-after.json`

**Interfaces:**
- Replaces: only the Tencent `stationery_member` pre-smoke database.
- Preserves: every Alibaba source file and database.

- [ ] **Step 1: Transfer the dump and uploads through the local machine**

Copy the source dump and upload archive to `/private/tmp`, verify source checksums, copy both to the Tencent migration backup directory, and verify target checksums. Do not pipe SQL through chat/tool output.

- [ ] **Step 2: Recreate only the Tencent target database**

With Tencent backend stopped, explicitly drop and recreate only `stationery_member` using utf8mb4/`utf8mb4_0900_ai_ci`, then import the verified compressed source dump.

- [ ] **Step 3: Apply the approved incremental schema**

Run `20260921_01_miniprogram_payment.sql` against the imported database. Do not run seed inserts from `01-schema.sql` against production data.

- [ ] **Step 4: Clear nonportable transient security data**

Within one transaction, truncate/delete only `login_session` and `verification_code`. Confirm all business tables remain unchanged.

- [ ] **Step 5: Restore uploads and ownership**

Extract into `/mnt/datadisk0/stationery-member/uploads`, reject absolute/path-traversal archive entries before extraction, and set `stationery:stationery` ownership. Do not overwrite files with different checksums without stopping for review.

- [ ] **Step 6: Capture and compare the target snapshot**

Run the same snapshot SQL on Tencent. Compare every business count, amount aggregate, maximum ID, and timestamp with the source. Expected differences are:

```text
login_session count: target 0
verification_code count: target 0
new schema tables: target present and initially empty
```

- [ ] **Step 7: Apply the second go/no-go checkpoint**

If any other count or aggregate differs, do not start Tencent. Preserve target evidence, restart Alibaba, and end maintenance. Continue only after exact reconciliation.

### Task 7: Start Tencent, perform read-only production smoke, and cut traffic

**Files:**
- Create locally: `../migration-evidence/2026-09-21/tencent-smoke.md`

**Interfaces:**
- Makes: Tencent the sole active application writer after all validation passes.
- Keeps: Alibaba application stopped and recoverable.

- [ ] **Step 1: Point current symlinks to the verified release**

Atomically update `/mnt/datadisk0/stationery-member/backend/current.jar` and `/mnt/datadisk0/stationery-member/frontend/current` to the checksum-verified release.

- [ ] **Step 2: Start backend and reload Nginx**

```bash
ssh tencent 'sudo systemctl start stationery-member-backend'
ssh tencent 'sudo systemctl reload nginx'
ssh tencent 'sudo systemctl is-active stationery-member-backend nginx mysql redis-server'
```

- [ ] **Step 3: Verify health and listener isolation**

Check `http://127.0.0.1:8090/api/health` on Tencent, then `http://101.35.96.147/api/health` and the SPA externally. Verify listeners show Java only on `127.0.0.1:8090`, MySQL/Redis only on loopback, and Nginx on public `80`.

- [ ] **Step 4: Perform read-only production smoke**

Using an existing active merchant, verify login, dashboard, member list/detail, transaction history, recharge tiers, profile, image retrieval, and logout. Do not perform a production recharge/consume/refund simply to test deployment; those mutation paths must already have passed automated and empty-database integration tests.

- [ ] **Step 5: Verify bookkeeping explicitly**

Confirm `/api/mini/payments/capability` reports effective `BOOKKEEPING` and the application log has no payment-configuration startup failure. In a controlled non-production test member prepared before cutover, verify bookkeeping recharge only if such a record exists and its cleanup is deterministic; otherwise mark this as automated-only rather than touching production balances.

- [ ] **Step 6: Declare the first-write boundary**

Record the timestamp when Tencent is opened for normal business. Before that timestamp, rollback is restart-Alibaba. After that timestamp, any rollback must first freeze Tencent and reconcile newly written rows.

### Task 8: Harden access and add local data-disk backups

**Files:**
- Create: `ops/tencent/backup-database.sh`
- Create: `ops/tencent/stationery-member-backup.service`
- Create: `ops/tencent/stationery-member-backup.timer`
- Create on Tencent: `/mnt/datadisk0/stationery-member/config/mysql-backup.cnf`

**Interfaces:**
- Produces: daily compressed MySQL backups with checksum and 14-day local retention.
- Restricts: public listeners to SSH and Nginx.

- [ ] **Step 1: Install the backup credential file and script**

The root-only credential file is mode `600`. The script writes a temporary dump within the data-disk backup directory, validates gzip, writes SHA-256, atomically renames on success, and deletes only matching backup/checksum pairs older than 14 days.

- [ ] **Step 2: Install and test the systemd timer**

Run one manual backup service execution, restore it into a temporary explicitly named validation database, compare schema/table counts, then drop only that temporary validation database. Enable the daily timer after restore validation succeeds.

- [ ] **Step 3: Enable UFW safely**

Allow `OpenSSH`, `80/tcp`, and `443/tcp` before enabling UFW. Confirm the Tencent Cloud security group has the same intended public ports; cloud-console changes remain a user/platform follow-up if no API access is available.

- [ ] **Step 4: Disable SSH password login only after a fresh key-only check**

From a separate local command run `ssh -o BatchMode=yes tencent true`. Only after it succeeds, install an sshd drop-in with `PasswordAuthentication no`, `KbdInteractiveAuthentication no`, and `PermitRootLogin no`; validate with `sshd -t`, reload SSH, and open another key-only session before closing the existing one.

- [ ] **Step 5: Run final security checks**

Verify UFW, public listeners, service users, config/key modes, data-disk mounts, timer state, and that logs do not contain secrets or callback payloads.

### Task 9: Update deployment documentation and preserve rollback evidence

**Files:**
- Modify: `../docs/部署文档.md`
- Modify: `ops/tencent/README.md`
- Create locally: `../migration-evidence/2026-09-21/final-report.md`

**Interfaces:**
- Produces: an operator-ready record of deployed release, counts, checksums, validation, and remaining external actions.

- [ ] **Step 1: Replace Alibaba-specific deployment instructions with Tencent architecture**

Document data-disk paths, services, release/rollback operations, bookkeeping default, backup/restore, logs, health checks, and the prohibition against enabling real payment without complete credentials.

- [ ] **Step 2: Record final evidence without secrets**

Include release ID, Git commit IDs, artifact hashes, source/target snapshot comparison, migration backup paths/hashes, service status, listener checks, smoke results, first-write timestamp, and which tests were automated versus manually exercised.

- [ ] **Step 3: Preserve Alibaba as stopped rollback source**

Do not delete or alter its database, application, or uploads. Record the exact restart command and the rule that post-first-write rollback requires Tencent delta reconciliation.

- [ ] **Step 4: List external follow-ups**

Record: ICP备案 approval, DNS cutover, TLS issuance/renewal, WeChat legal-domain setup, exposed credential rotation, service-provider onboarding, per-store special-merchant onboarding, real-funds payment/notification test, WeChat refund implementation and acceptance before enabling real-transaction recovery actions, and COS/off-host backup.

- [ ] **Step 5: Run final plan-specific verification**

```bash
git diff --check
shellcheck ops/tencent/*.sh
ssh tencent 'findmnt /mnt/datadisk0 /var/lib/mysql /var/lib/redis; systemctl is-active mysql redis-server nginx stationery-member-backend; systemctl is-enabled stationery-member-backup.timer; sudo ss -lntp'
```

Expected: clean diff, shell scripts pass, mounts/services/timer are correct, and only intended ports listen publicly.
