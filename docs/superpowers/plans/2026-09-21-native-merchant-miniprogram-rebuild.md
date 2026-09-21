# Native Merchant Miniprogram Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the mock-first independent merchant miniprogram with a clean native WeChat miniprogram that uses the existing Spring Boot business rules, supports bookkeeping without merchant credentials, and displays a dynamic Native-payment QR code only for stores that are explicitly enabled for real payment.

**Architecture:** The miniprogram is a thin client. It stores a 12-hour merchant Bearer token, calls the shared `/api/merchant/**` endpoints for all member, wallet, recharge-tier, transaction, and profile operations, and calls `/api/mini/**` only for WeChat binding and real-payment orchestration. Pure flow modules are separated from page adapters so Node tests can verify business branching without a WeChat runtime.

**Tech Stack:** Native WeChat miniprogram JavaScript/WXML/WXSS, Node.js built-in test runner, `miniprogram-ci`, `weapp-qrcode`.

**Spec:** `docs/superpowers/specs/2026-09-21-tencent-cloud-miniprogram-migration-design.md`

**Dependency:** Backend tasks 1-6 in `docs/superpowers/plans/2026-09-21-backend-miniprogram-payment-foundation.md` must be complete before live integration. Legacy source archival in Task 1 below must complete before either old miniprogram directory is removed.

## Global Constraints

- Do not reuse the independent `merchant-mini-backend` runtime, `mini_*` schema, mock database, or `/mini-api` contract.
- Do not embed AppSecret, upload keys, merchant numbers, payment keys, access keys, passwords, or production-only credentials in the miniprogram package.
- The miniprogram must remain usable through account/password login when WeChat code login is not configured.
- The server-provided payment capability is authoritative. The client must not infer real-payment availability from local configuration.
- In `BOOKKEEPING`, selecting `WECHAT` continues to call the existing recharge endpoint and records a ledger category only.
- In `REAL`, a WeChat recharge creates a payment order and waits for server-confirmed success; it must not call the direct recharge endpoint.
- Consumption always calls the existing wallet-debit endpoint and never invokes real payment.
- Every mutation disables repeat submission and carries one stable idempotency key across retries.
- The AppID may remain in `project.config.json`; all secret material stays outside the project.

## Review Focus

- Every API path and response shape matches the shared Java backend rather than the deleted mini backend.
- Authentication failure clears local state and returns to login without creating redirect loops.
- A pending Native order never appears as a successful wallet recharge.
- Closing or leaving the QR page stops polling and does not create a second order on re-entry.
- Page actions cover the existing Vue business surface: dashboard, recharge tiers, member CRUD, lookup, recharge, consume, transaction history, reverse/refund, profile, password, and image upload.

---

### Task 1: Archive and remove the independent miniprogram implementation

**Files:**
- Archive source: `../merchant-mini-backend/`
- Archive source: `../merchant-miniprogram/`
- Create on Tencent: `/mnt/datadisk0/stationery-member/backups/legacy-mini/stationery-legacy-mini-20260921.tar.gz`
- Recreate: `../merchant-miniprogram/`

**Interfaces:**
- Produces: a checksum-verified source archive on the Tencent data disk.
- Removes: the active independent Java mini backend and mock-first client trees after archive verification.

- [ ] **Step 1: Inventory the exact legacy source before archiving**

Run from the workspace root:

```bash
find merchant-mini-backend merchant-miniprogram -path '*/target' -prune -o -path '*/node_modules' -prune -o -type f -print | sort
```

Expected: only the two named legacy directories are listed; inspect the output before deletion.

- [ ] **Step 2: Build a source-only archive and checksum**

```bash
tar --exclude='merchant-mini-backend/target' --exclude='merchant-miniprogram/node_modules' -czf /private/tmp/stationery-legacy-mini-20260921.tar.gz merchant-mini-backend merchant-miniprogram
shasum -a 256 /private/tmp/stationery-legacy-mini-20260921.tar.gz
```

Expected: one non-empty archive and a recorded SHA-256 value.

- [ ] **Step 3: Copy the archive to the Tencent data disk and verify it remotely**

```bash
ssh tencent 'sudo install -d -m 750 -o ubuntu -g ubuntu /mnt/datadisk0/stationery-member/backups/legacy-mini'
scp /private/tmp/stationery-legacy-mini-20260921.tar.gz tencent:/mnt/datadisk0/stationery-member/backups/legacy-mini/
ssh tencent 'sha256sum /mnt/datadisk0/stationery-member/backups/legacy-mini/stationery-legacy-mini-20260921.tar.gz'
```

Expected: local and remote checksums match exactly.

- [ ] **Step 4: Remove only the two verified legacy directories, then create the clean client root**

```bash
rm -rf merchant-mini-backend merchant-miniprogram
mkdir merchant-miniprogram
```

Expected: `merchant-mini-backend` no longer exists and `merchant-miniprogram` is empty. The Tencent archive is the recovery source.

- [ ] **Step 5: Record the archive path and checksum in the migration evidence directory**

Create `../migration-evidence/2026-09-21/legacy-mini-archive.sha256` containing only the checksum and remote archive path; do not include credentials.

### Task 2: Scaffold the clean native miniprogram and executable contracts

**Files:**
- Create: `../merchant-miniprogram/package.json`
- Create: `../merchant-miniprogram/package-lock.json`
- Create: `../merchant-miniprogram/project.config.json`
- Create: `../merchant-miniprogram/.gitignore`
- Create: `../merchant-miniprogram/README.md`
- Create: `../merchant-miniprogram/miniprogram/app.js`
- Create: `../merchant-miniprogram/miniprogram/app.json`
- Create: `../merchant-miniprogram/miniprogram/app.wxss`
- Create: `../merchant-miniprogram/miniprogram/sitemap.json`
- Create: `../merchant-miniprogram/tests/structure.test.cjs`
- Create: `../merchant-miniprogram/tests/no-secrets.test.cjs`

**Interfaces:**
- Produces: a native miniprogram project with four tabs: home, members, cashier, and profile.
- Produces: test scripts `test`, `check`, and `upload`; upload remains an explicit later action.

- [ ] **Step 1: Write failing project-structure and secret-safety tests**

Assert the project has the AppID, privacy check, required pages/components, a four-item tab bar, and no source containing names or patterns for AppSecret, API v3 keys, private keys, AccessKey secrets, or `useMock`.

- [ ] **Step 2: Run the tests and verify the clean directory fails**

Run from `merchant-miniprogram`:

```bash
node --test tests/structure.test.cjs tests/no-secrets.test.cjs
```

Expected: FAIL because the project files do not exist.

- [ ] **Step 3: Create package scripts and dependencies**

```json
{
  "scripts": {
    "test": "node --test tests/*.test.cjs",
    "check": "node tests/static-check.cjs",
    "upload": "node scripts/upload.cjs"
  },
  "dependencies": {
    "weapp-qrcode": "^1.0.0"
  },
  "devDependencies": {
    "miniprogram-ci": "^2.1.31"
  }
}
```

Run `npm install` to create the lockfile. Configure WeChat npm packaging so the QR dependency is included in the uploaded miniprogram.

- [ ] **Step 4: Create the app shell and route inventory**

Required pages:

```text
pages/auth/login
pages/home/index
pages/marketing/tiers
pages/member/index
pages/member/form
pages/member/detail
pages/cashier/index
pages/payment/qr
pages/transaction/result
pages/profile/index
pages/profile/edit
```

Required reusable components: `amount-keypad`, `member-card`, `transaction-card`, `status-tag`, `empty-state`, and `payment-qr`.

- [ ] **Step 5: Run the project contracts**

```bash
npm test
npm run check
```

Expected: PASS; no mock runtime or secret-bearing file exists.

### Task 3: Implement environment, request, session, and API adapters

**Files:**
- Create: `../merchant-miniprogram/miniprogram/config/env.js`
- Create: `../merchant-miniprogram/miniprogram/utils/request.js`
- Create: `../merchant-miniprogram/miniprogram/utils/auth.js`
- Create: `../merchant-miniprogram/miniprogram/utils/idempotency.js`
- Create: `../merchant-miniprogram/miniprogram/utils/format.js`
- Create: `../merchant-miniprogram/miniprogram/api/auth.js`
- Create: `../merchant-miniprogram/miniprogram/api/merchant.js`
- Create: `../merchant-miniprogram/miniprogram/api/payment.js`
- Create: `../merchant-miniprogram/tests/request.test.cjs`
- Create: `../merchant-miniprogram/tests/api-contract.test.cjs`

**Interfaces:**
- Uses: `http://101.35.96.147` for developer-tool integration before ICP/TLS.
- Uses: `https://api.usepenciles.com` only after备案、TLS and WeChat legal-domain configuration are complete.
- Produces: uniform unwrapping of backend `{code,message,data}` responses.

- [ ] **Step 1: Write failing adapter tests**

Cover Bearer injection, query serialization, response unwrapping, upload headers, 401/423 session clearing, timeout/network messages, and exact route mapping.

- [ ] **Step 2: Define public-only environment configuration**

Expose `apiBaseUrl` and timeout. Do not include `useMock`, AppSecret, upload-key path, service-provider merchant data, or payment credentials.

- [ ] **Step 3: Implement storage and request adapters**

Store only token, expiry, and public store profile. Reject expired tokens before requests. On 401/423, clear state and `wx.reLaunch` once to `/pages/auth/login`.

- [ ] **Step 4: Map the shared backend routes exactly**

```text
POST /api/auth/merchant/login
GET  /api/auth/merchant/me
POST /api/auth/merchant/logout
POST /api/mini/auth/code-login
POST /api/mini/auth/bind
GET/PUT /api/merchant/profile
PUT  /api/merchant/password
GET  /api/merchant/dashboard
GET/PUT /api/merchant/recharge-tiers[/{tierNo}]
GET  /api/merchant/campaigns
GET/POST/PUT/DELETE /api/merchant/members[/{id}]
GET  /api/merchant/members/lookup
GET  /api/merchant/members/{id}/transactions
POST /api/merchant/transactions/recharge
POST /api/merchant/transactions/consume
GET  /api/merchant/transactions/recent
POST /api/merchant/transactions/{id}/reverse
POST /api/merchant/transactions/{id}/refund
POST /api/files/images
GET  /api/mini/payments/capability
POST /api/mini/payments/native-orders
GET  /api/mini/payments/native-orders/{outTradeNo}
```

- [ ] **Step 5: Run adapter tests**

```bash
node --test --test-name-pattern='request|API contract' tests/*.test.cjs
```

Expected: PASS without a WeChat runtime or network access.

### Task 4: Implement WeChat code login with account/password fallback

**Files:**
- Create: `../merchant-miniprogram/miniprogram/services/login-flow.js`
- Create: `../merchant-miniprogram/miniprogram/pages/auth/login.js`
- Create: `../merchant-miniprogram/miniprogram/pages/auth/login.json`
- Create: `../merchant-miniprogram/miniprogram/pages/auth/login.wxml`
- Create: `../merchant-miniprogram/miniprogram/pages/auth/login.wxss`
- Create: `../merchant-miniprogram/tests/login-flow.test.cjs`

**Interfaces:**
- Consumes: `wx.login`, code-login, bind, and existing merchant-login endpoints.
- Produces: one local Bearer session shape regardless of login path.

- [ ] **Step 1: Write failing flow tests**

Cover an already-bound code login, an unbound account requiring credentials, successful binding, code-login unavailable because server configuration is missing, explicit account/password fallback, disabled store, and duplicate-submit suppression.

- [ ] **Step 2: Implement the pure login state machine**

The state machine returns `CHECKING_WECHAT`, `NEEDS_BINDING`, `ACCOUNT_LOGIN`, `SUBMITTING`, `AUTHENTICATED`, or `FAILED`. It never stores the password and never accepts an `openid` from UI data.

- [ ] **Step 3: Implement the login page**

Try `wx.login` on page entry. If code login is unavailable or unbound, show the existing merchant account/password form. Use `/api/mini/auth/bind` when a valid WeChat code is present; otherwise use `/api/auth/merchant/login`.

- [ ] **Step 4: Verify login behavior**

```bash
node --test --test-name-pattern='login' tests/*.test.cjs
npm run check
```

Expected: PASS; AppSecret is never present client-side.

### Task 5: Implement the existing Java/Vue business surface

**Files:**
- Create: `../merchant-miniprogram/miniprogram/pages/home/*`
- Create: `../merchant-miniprogram/miniprogram/pages/marketing/tiers.*`
- Create: `../merchant-miniprogram/miniprogram/pages/member/index.*`
- Create: `../merchant-miniprogram/miniprogram/pages/member/form.*`
- Create: `../merchant-miniprogram/miniprogram/pages/member/detail.*`
- Create: `../merchant-miniprogram/miniprogram/pages/cashier/index.*`
- Create: `../merchant-miniprogram/miniprogram/pages/transaction/result.*`
- Create: `../merchant-miniprogram/miniprogram/pages/profile/index.*`
- Create: `../merchant-miniprogram/miniprogram/pages/profile/edit.*`
- Create: `../merchant-miniprogram/miniprogram/components/*`
- Create: `../merchant-miniprogram/tests/business-contract.test.cjs`

**Interfaces:**
- Consumes: the shared merchant endpoints only.
- Preserves: recharge-tier selection, gift calculation display, wallet debit order, reversal/refund rules, pagination, field validation, and store isolation from the backend.

- [ ] **Step 1: Write a failing page/action contract test**

Assert page files and actionable bindings exist for dashboard refresh, tier edit, member search/create/edit/delete/detail/history, full-mobile and last-four lookup, recharge, consume, transaction reverse/refund, profile/avatar/password edit, logout, loading, empty, and error states.

- [ ] **Step 2: Implement home and recharge-tier pages**

Render the dashboard and current campaign from server data. Edit the four existing recharge tiers through the server endpoint; display validation errors from the backend unchanged.

- [ ] **Step 3: Implement member list, form, and detail pages**

Support keyword pagination, server-side lookup, add/edit/delete, avatar upload, wallet totals, and paginated transaction history. Treat Java `Long` identifiers as opaque strings in the client to avoid numeric precision loss.

- [ ] **Step 4: Implement cashier bookkeeping paths**

For recharge, show server recharge tiers and selected payment category. For consumption, require an item name and call only `/transactions/consume`. Reuse one idempotency key while a submission is pending or explicitly retried.

- [ ] **Step 5: Implement transaction recovery actions**

Show reverse/refund only when the server transaction state allows it, require confirmation, prevent double submission, refresh member/detail data after success, and surface server errors.

- [ ] **Step 6: Implement profile and password editing**

Expose only fields supported by the shared backend: shop name, avatar, and password. Display contact mobile and address read-only; do not recreate unsupported mock-only location persistence.

- [ ] **Step 7: Run the business contract suite**

```bash
node --test --test-name-pattern='business contract|idempotency|format' tests/*.test.cjs
npm run check
```

Expected: PASS with all required actions present and no unsupported mock-only APIs.

### Task 6: Implement capability-driven Native-payment QR flow

**Files:**
- Create: `../merchant-miniprogram/miniprogram/services/payment-flow.js`
- Create: `../merchant-miniprogram/miniprogram/components/payment-qr/payment-qr.js`
- Create: `../merchant-miniprogram/miniprogram/components/payment-qr/payment-qr.json`
- Create: `../merchant-miniprogram/miniprogram/components/payment-qr/payment-qr.wxml`
- Create: `../merchant-miniprogram/miniprogram/components/payment-qr/payment-qr.wxss`
- Create: `../merchant-miniprogram/miniprogram/pages/payment/qr.js`
- Create: `../merchant-miniprogram/miniprogram/pages/payment/qr.json`
- Create: `../merchant-miniprogram/miniprogram/pages/payment/qr.wxml`
- Create: `../merchant-miniprogram/miniprogram/pages/payment/qr.wxss`
- Modify: `../merchant-miniprogram/miniprogram/pages/cashier/index.js`
- Modify: `../merchant-miniprogram/miniprogram/pages/cashier/index.wxml`
- Create: `../merchant-miniprogram/tests/payment-flow.test.cjs`

**Interfaces:**
- Consumes: server payment capability, create-order, and query-order endpoints.
- Produces: a customer-scannable dynamic QR for `codeUrl` and a server-confirmed final result.

- [ ] **Step 1: Write failing payment-flow tests**

Cover bookkeeping WeChat recharge, real-mode order creation, QR waiting state, query-confirmed success, callback-confirmed success observed by polling, failed/closed/expired states, page unload stopping timers, query network retry with capped backoff, and no direct recharge call in real mode.

- [ ] **Step 2: Implement capability branching in cashier**

Load capability on page show. If `effectiveMode=BOOKKEEPING`, retain ordinary payment-category selection. If `realPaymentAvailable=true` and payment method is WeChat, create one Native order using the cashier idempotency key and navigate with only `outTradeNo`; do not put `codeUrl` or sensitive content in the route.

- [ ] **Step 3: Render the server-provided code URL as QR**

The QR component receives `codeUrl` in memory, renders it with `weapp-qrcode`, and clears its canvas/data on detach. Do not log or persist `codeUrl`.

- [ ] **Step 4: Implement bounded order polling**

Query the order immediately, then every 2 seconds for the first 30 seconds and every 5 seconds thereafter until the server expiry. Stop on `SUCCESS`, `FAILED`, `CLOSED`, navigation away, or expiry. A retry button queries the same order before offering a new order.

- [ ] **Step 5: Verify no premature wallet mutation**

```bash
node --test --test-name-pattern='payment' tests/*.test.cjs
```

Expected: PASS; real mode never calls `/api/merchant/transactions/recharge`, and only a server `SUCCESS` renders payment success.

### Task 7: Add upload tooling and complete local/client verification

**Files:**
- Create: `../merchant-miniprogram/scripts/upload.cjs`
- Modify: `../merchant-miniprogram/README.md`
- Modify: `../merchant-miniprogram/tests/static-check.cjs`

**Interfaces:**
- Produces: an upload script that reads key path and release metadata from environment variables only.
- Does not perform: an actual WeChat upload in this task.

- [ ] **Step 1: Implement guarded upload tooling**

Require `WECHAT_APPID`, `WECHAT_UPLOAD_PRIVATE_KEY`, `WECHAT_UPLOAD_VERSION`, and `WECHAT_UPLOAD_DESC`. Validate the key path is outside the project, invoke `miniprogram-ci`, and exclude tests/scripts from the release package.

- [ ] **Step 2: Document developer-tool and production prerequisites**

Document that IP HTTP works only for developer-tool testing with domain validation disabled. Production requires备案、TLS, `https://api.usepenciles.com` as a legal request/upload domain, a rotated upload key, and explicit release authorization.

- [ ] **Step 3: Run the full miniprogram verification**

```bash
npm ci
npm test
npm run check
```

Expected: all tests and static checks PASS.

- [ ] **Step 4: Perform a developer-tool smoke test**

Verify account/password login, dashboard, member CRUD, recharge-tier edit, bookkeeping recharge, consume, history, reverse/refund, profile edit, logout, and developer console/network errors against Tencent. Real payment remains mock-gateway tested until merchant credentials exist.

- [ ] **Step 5: Capture verification evidence**

Write commands, test totals, screenshots, and untested external prerequisites to `../migration-evidence/2026-09-21/miniprogram-verification.md`. Never place tokens or QR contents in evidence.
