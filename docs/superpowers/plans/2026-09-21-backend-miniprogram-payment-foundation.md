# Backend Miniprogram and Payment Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing Spring Boot backend so Web and the rebuilt merchant miniprogram share one business database, while bookkeeping remains the default and service-provider Native payment can be enabled later per store.

**Architecture:** Keep `MainFlowService` as the single wallet and transaction authority. Add Bearer-token merchant authentication, WeChat miniprogram binding, store-level payment capability, a payment-order state machine, and a gateway boundary whose real implementation uses WeChat Pay service-provider V3 Native payment. Missing payment credentials must not prevent startup or bookkeeping.

**Tech Stack:** Java 17, Spring Boot 3.5.15, MyBatis 3.0.5, MySQL 8, Redis, JUnit 5, Mockito, WeChat Pay Java SDK 0.2.17.

**Spec:** `docs/superpowers/specs/2026-09-21-tencent-cloud-miniprogram-migration-design.md`

## Global Constraints

- Preserve all existing `/api/auth`, `/api/merchant`, `/api/admin`, wallet, recharge, consume, reverse, refund, and Vue contracts.
- Use `stationery_member` as the only business database; do not create `mini_*` tables or a second backend.
- `STATIONERY_PAYMENT_MODE` defaults to `BOOKKEEPING`; startup must succeed with every WeChat Pay credential absent.
- A store without an enabled `sub_mchid` remains in `BOOKKEEPING` even when the platform gateway is configured.
- Real payment uses service-provider V3 Native payment: the merchant miniprogram displays `code_url`, and only a verified callback or order query may credit the wallet.
- Never log passwords, tokens, AppSecret, API v3 key, private keys, complete callback bodies, `code_url`, or full mobile numbers.

## Review Focus

- Missing, partial, or malformed payment configuration must leave bookkeeping usable and must never instantiate a failing real gateway during startup.
- Merchant Bearer tokens must work only for merchant APIs; admin APIs must continue requiring the admin Cookie.
- A token or payment order from store A must never read or mutate store B data.
- Repeated or out-of-order payment callbacks and queries must create at most one successful recharge transaction.
- Failed, closed, expired, unsigned, or amount-mismatched payment results must not change wallet balances.

---

### Task 1: Add the shared miniprogram and payment schema

**Files:**
- Create: `src/main/resources/db/migration/20260921_01_miniprogram_payment.sql`
- Modify: `src/main/resources/db/init/01-schema.sql`
- Create: `src/test/java/com/gechuang/stationery/schema/MiniprogramPaymentSchemaTest.java`

**Interfaces:**
- Produces: `store_wechat_binding`, `store_wechat_pay_config`, and `wechat_payment_order` tables used by all later backend tasks.
- Produces: unique constraints for `openid`, `store_id`, `out_trade_no`, `wallet_transaction_id`, and `idempotency_key`.

- [ ] **Step 1: Write a failing schema contract test**

```java
@Test
void schemaContainsSharedMiniprogramAndPaymentTables() throws Exception {
    String sql = Files.readString(Path.of("src/main/resources/db/migration/20260921_01_miniprogram_payment.sql"));
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS store_wechat_binding");
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS store_wechat_pay_config");
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS wechat_payment_order");
    assertThat(sql).contains("UNIQUE KEY uk_store_wechat_binding_openid");
    assertThat(sql).contains("UNIQUE KEY uk_wechat_payment_order_idempotency");
}
```

- [ ] **Step 2: Run the test and verify it fails because the migration does not exist**

Run: `mvn -q -Dtest=MiniprogramPaymentSchemaTest test`

Expected: FAIL reading `20260921_01_miniprogram_payment.sql`.

- [ ] **Step 3: Add the incremental schema**

Create the three tables with these required columns:

```sql
CREATE TABLE IF NOT EXISTS store_wechat_binding (
  id BIGINT NOT NULL AUTO_INCREMENT,
  store_id BIGINT NOT NULL,
  openid VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_store_wechat_binding_openid (openid),
  KEY idx_store_wechat_binding_store (store_id),
  CONSTRAINT fk_store_wechat_binding_store FOREIGN KEY (store_id) REFERENCES store_account (id)
);
```

Define `store_wechat_pay_config` with unique `store_id`, `payment_mode`, nullable `sub_mchid`, `authorization_status`, and timestamps. Define `wechat_payment_order` with `out_trade_no`, `wechat_transaction_id`, nullable unique `wallet_transaction_id`, `store_id`, `member_id`, recharge and gift amounts, `sp_mchid`, `sub_mchid`, `appid`, `code_url`, `idempotency_key`, `status`, `failure_code`, `failure_message`, `expires_at`, `last_query_at`, `paid_at`, and timestamps. Use `DECIMAL(12,2)` for money and foreign keys to existing store/member/transaction tables.

- [ ] **Step 4: Copy the same fresh-install definitions into `01-schema.sql`**

Place them after `wallet_transaction` so referenced tables already exist.

- [ ] **Step 5: Run the schema contract and SQL formatting checks**

Run: `mvn -q -Dtest=MiniprogramPaymentSchemaTest test`

Expected: PASS.

- [ ] **Step 6: Commit the schema task**

```bash
git add src/main/resources/db/init/01-schema.sql src/main/resources/db/migration/20260921_01_miniprogram_payment.sql src/test/java/com/gechuang/stationery/schema/MiniprogramPaymentSchemaTest.java
git commit -m "feat: add shared miniprogram payment schema"
```

### Task 2: Accept merchant Bearer tokens without weakening admin authentication

**Files:**
- Modify: `src/main/java/com/gechuang/stationery/auth/AuthController.java`
- Modify: `src/main/java/com/gechuang/stationery/mainflow/MainFlowService.java`
- Create: `src/test/java/com/gechuang/stationery/mainflow/MainFlowBearerAuthenticationTest.java`

**Interfaces:**
- Consumes: existing `login_session` records created by `loginMerchant`.
- Produces: `requireMerchant(HttpServletRequest)` and merchant logout that accept either `MERCHANT_TOKEN` Cookie or `Authorization: Bearer TOKEN_VALUE`.
- Preserves: `requireAdmin(HttpServletRequest)` accepts only `ADMIN_TOKEN` Cookie.

- [ ] **Step 1: Write failing tests for Bearer, Cookie fallback, merchant logout, and admin isolation**

```java
@Test
void merchantBearerTokenLoadsTheBoundStore() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer merchant-token");
    when(mapper.selectStoreByToken(eq("merchant-token"), any())).thenReturn(activeStore());
    assertThat(service.requireMerchant(request).get("id")).isEqualTo(1L);
}

@Test
void adminDoesNotAcceptMerchantBearerToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer merchant-token");
    assertThatThrownBy(() -> service.requireAdmin(request)).isInstanceOf(BusinessException.class);
}
```

- [ ] **Step 2: Run the tests and verify Bearer authentication fails**

Run: `mvn -q -Dtest=MainFlowBearerAuthenticationTest test`

Expected: FAIL because `requireMerchant` only reads cookies.

- [ ] **Step 3: Implement strict merchant token extraction**

```java
private String merchantTokenFromRequest(HttpServletRequest request) {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (StringUtils.hasText(authorization) && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
        return authorization.substring(7).trim();
    }
    return tokenFromCookie(request, MERCHANT_TOKEN);
}
```

Reject an empty Bearer value and leave `requireAdmin` unchanged. Update merchant logout to delete the same Bearer-or-Cookie session selected by merchant authentication. Admin logout must continue reading only `ADMIN_TOKEN` Cookie and must never accept a merchant Bearer token.

- [ ] **Step 4: Run focused auth tests**

Run: `mvn -q -Dtest=MainFlowBearerAuthenticationTest,MainFlowRealDatabaseControllerTest test`

Expected: PASS.

- [ ] **Step 5: Commit the auth compatibility task**

```bash
git add src/main/java/com/gechuang/stationery/auth/AuthController.java src/main/java/com/gechuang/stationery/mainflow/MainFlowService.java src/test/java/com/gechuang/stationery/mainflow/MainFlowBearerAuthenticationTest.java
git commit -m "feat: support merchant bearer sessions"
```

### Task 3: Add optional WeChat code login and account binding

**Files:**
- Create: `src/main/java/com/gechuang/stationery/miniprogram/MiniProgramProperties.java`
- Create: `src/main/java/com/gechuang/stationery/miniprogram/WechatCodeSessionClient.java`
- Create: `src/main/java/com/gechuang/stationery/miniprogram/HttpWechatCodeSessionClient.java`
- Create: `src/main/java/com/gechuang/stationery/miniprogram/MiniProgramAuthService.java`
- Create: `src/main/java/com/gechuang/stationery/miniprogram/MiniProgramAuthController.java`
- Modify: `src/main/java/com/gechuang/stationery/mainflow/MainFlowMapper.java`
- Modify: `src/main/resources/mapper/mainflow/MainFlowMapper.xml`
- Modify: `src/main/resources/application.yml`
- Create: `src/test/java/com/gechuang/stationery/miniprogram/MiniProgramAuthServiceTest.java`

**Interfaces:**
- Produces: `POST /api/mini/auth/code-login` with `{code}`.
- Produces: `POST /api/mini/auth/bind` with `{code, account, password}`.
- Produces: `WechatCodeSessionClient.exchange(String code) -> CodeSession(openid, sessionKey)`.
- Consumes: `MainFlowService.loginMerchant(account, password)` and shared `login_session`.

- [ ] **Step 1: Write failing service tests**

Cover an existing binding returning a token, an unbound `openid` returning `bindingRequired=true`, a successful bind, duplicate binding conflict, disabled store rejection, and missing AppSecret returning a configuration error without affecting account/password login.

- [ ] **Step 2: Run the tests and verify the classes are missing**

Run: `mvn -q -Dtest=MiniProgramAuthServiceTest test`

Expected: compilation FAIL.

- [ ] **Step 3: Add typed miniprogram configuration**

```yaml
stationery:
  miniprogram:
    app-id: ${WECHAT_MINI_APP_ID:}
    app-secret: ${WECHAT_MINI_APP_SECRET:}
```

Use `RestClient` to call `https://api.weixin.qq.com/sns/jscode2session`. Convert WeChat `errcode` responses into a stable business error and never log the request URL because it contains AppSecret.

- [ ] **Step 4: Add binding mapper methods and transactional service logic**

Required mapper methods:

```java
Map<String, Object> selectStoreByWechatOpenid(@Param("openid") String openid);
int insertStoreWechatBinding(@Param("storeId") Long storeId, @Param("openid") String openid);
```

`bind` must exchange the code server-side, validate the existing merchant credentials through `loginMerchant`, insert the binding, and return that login result. Do not accept `openid` from the client.

- [ ] **Step 5: Add controller endpoints and validation**

Require a nonblank code; require account/password only for bind. Return the same token/user/expiresIn shape used by existing merchant login so the miniprogram has one session contract.

- [ ] **Step 6: Run the miniprogram auth and existing auth tests**

Run: `mvn -q -Dtest=MiniProgramAuthServiceTest,MainFlowBearerAuthenticationTest,MainFlowRealDatabaseControllerTest test`

Expected: PASS.

- [ ] **Step 7: Commit the miniprogram authentication task**

```bash
git add src/main/java/com/gechuang/stationery/miniprogram src/main/java/com/gechuang/stationery/mainflow/MainFlowMapper.java src/main/resources/mapper/mainflow/MainFlowMapper.xml src/main/resources/application.yml src/test/java/com/gechuang/stationery/miniprogram
git commit -m "feat: add miniprogram merchant binding"
```

### Task 4: Add store payment capability with safe bookkeeping defaults

**Files:**
- Create: `src/main/java/com/gechuang/stationery/payment/PaymentMode.java`
- Create: `src/main/java/com/gechuang/stationery/payment/WechatPayProperties.java`
- Create: `src/main/java/com/gechuang/stationery/payment/StorePaymentCapabilityService.java`
- Create: `src/main/java/com/gechuang/stationery/payment/PaymentCapabilityController.java`
- Modify: `src/main/java/com/gechuang/stationery/mainflow/MainFlowMapper.java`
- Modify: `src/main/resources/mapper/mainflow/MainFlowMapper.xml`
- Modify: `src/main/resources/application.yml`
- Create: `src/test/java/com/gechuang/stationery/payment/StorePaymentCapabilityServiceTest.java`

**Interfaces:**
- Produces: `GET /api/mini/payments/capability` returning platform mode, effective store mode, authorization status, and `realPaymentAvailable`.
- Consumes: current merchant identity and `store_wechat_pay_config`.

- [ ] **Step 1: Write failing default-mode tests**

```java
@Test
void missingStoreConfigDefaultsToBookkeeping() {
    when(mapper.selectStoreWechatPayConfig(1L)).thenReturn(null);
    PaymentCapability result = service.capability(1L);
    assertThat(result.effectiveMode()).isEqualTo(PaymentMode.BOOKKEEPING);
    assertThat(result.realPaymentAvailable()).isFalse();
}
```

Also cover partial platform credentials, a pending store, and a fully enabled store.

- [ ] **Step 2: Run the test and verify it fails**

Run: `mvn -q -Dtest=StorePaymentCapabilityServiceTest test`

Expected: compilation FAIL.

- [ ] **Step 3: Add configuration with safe defaults**

```yaml
stationery:
  payment:
    mode: ${STATIONERY_PAYMENT_MODE:BOOKKEEPING}
    wechat:
      sp-app-id: ${WECHAT_PAY_SP_APP_ID:}
      sp-mchid: ${WECHAT_PAY_SP_MCHID:}
      merchant-serial-number: ${WECHAT_PAY_MERCHANT_SERIAL_NUMBER:}
      private-key-path: ${WECHAT_PAY_PRIVATE_KEY_PATH:}
      api-v3-key: ${WECHAT_PAY_API_V3_KEY:}
      public-key-id: ${WECHAT_PAY_PUBLIC_KEY_ID:}
      public-key-path: ${WECHAT_PAY_PUBLIC_KEY_PATH:}
      notify-url: ${WECHAT_PAY_NOTIFY_URL:}
```

- [ ] **Step 4: Implement effective-mode rules and endpoint**

`realPaymentAvailable` is true only when platform mode is `WECHAT_SERVICE_PROVIDER`, all platform credentials are present, the store mode is `REAL`, `sub_mchid` is present, and authorization status is `ENABLED`.

- [ ] **Step 5: Run focused tests and verify application context starts with no payment environment variables**

Run: `mvn -q -Dtest=StorePaymentCapabilityServiceTest,RestResponseTest test`

Expected: PASS.

- [ ] **Step 6: Commit the capability task**

```bash
git add src/main/java/com/gechuang/stationery/payment src/main/java/com/gechuang/stationery/mainflow/MainFlowMapper.java src/main/resources/mapper/mainflow/MainFlowMapper.xml src/main/resources/application.yml src/test/java/com/gechuang/stationery/payment
git commit -m "feat: add safe payment capability switches"
```

### Task 5: Implement the service-provider Native payment gateway boundary

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/gechuang/stationery/payment/gateway/WechatPaymentGateway.java`
- Create: `src/main/java/com/gechuang/stationery/payment/gateway/DisabledWechatPaymentGateway.java`
- Create: `src/main/java/com/gechuang/stationery/payment/gateway/ServiceProviderNativePaymentGateway.java`
- Create: `src/main/java/com/gechuang/stationery/payment/gateway/WechatGatewayConfiguration.java`
- Create: `src/test/java/com/gechuang/stationery/payment/gateway/WechatGatewayConfigurationTest.java`

**Interfaces:**
- Produces: `createNativeOrder`, `queryByOutTradeNo`, `closeOrder`, and `parseNotification` gateway methods.
- Uses: `com.wechat.pay.java.service.partnerpayments.nativepay.NativePayService` and `NotificationParser` from official SDK 0.2.17.

- [ ] **Step 1: Write context tests for disabled and enabled gateway selection**

Verify that the default context creates `DisabledWechatPaymentGateway`, starts with no WeChat credentials, and never attempts network access. Verify that a complete test property set selects `ServiceProviderNativePaymentGateway` while the SDK client itself is injected/mocked.

- [ ] **Step 2: Run the tests and verify the gateway classes are missing**

Run: `mvn -q -Dtest=WechatGatewayConfigurationTest test`

Expected: compilation FAIL.

- [ ] **Step 3: Add the official SDK**

```xml
<dependency>
    <groupId>com.github.wechatpay-apiv3</groupId>
    <artifactId>wechatpay-java</artifactId>
    <version>0.2.17</version>
</dependency>
```

- [ ] **Step 4: Define gateway records and disabled behavior**

```java
public interface WechatPaymentGateway {
    NativeOrder createNativeOrder(CreateNativeOrder command);
    GatewayPayment queryByOutTradeNo(String outTradeNo, String subMchid);
    void closeOrder(String outTradeNo, String subMchid);
    GatewayPayment parseNotification(WechatNotification notification);
}
```

The disabled implementation throws `BusinessException(BIZ_409, "微信真实支付尚未配置")` only when a real-payment method is invoked.

- [ ] **Step 5: Implement service-provider Native calls and notification parsing**

Build one reusable SDK `Config` from the platform service-provider credentials. Populate `sp_appid`, `sp_mchid`, `sub_mchid`, `description`, `out_trade_no`, `notify_url`, amount in fen, and store scene information. Convert yuan to fen with exact `BigDecimal` arithmetic and reject fractions smaller than one fen. Use the raw callback body plus `Wechatpay-Serial`, `Wechatpay-Signature`, `Wechatpay-Timestamp`, and `Wechatpay-Nonce` with `NotificationParser`.

- [ ] **Step 6: Run gateway configuration tests and dependency compilation**

Run: `mvn -q -Dtest=WechatGatewayConfigurationTest test`

Expected: PASS without real credentials or network calls.

- [ ] **Step 7: Commit the gateway task**

```bash
git add pom.xml src/main/java/com/gechuang/stationery/payment/gateway src/test/java/com/gechuang/stationery/payment/gateway
git commit -m "feat: add service provider native payment gateway"
```

### Task 6: Add idempotent payment-order orchestration and callbacks

**Files:**
- Modify: `src/main/java/com/gechuang/stationery/StationeryMemberApplication.java`
- Create: `src/main/java/com/gechuang/stationery/payment/WechatPaymentOrderService.java`
- Create: `src/main/java/com/gechuang/stationery/payment/WechatPaymentOrderExpiryJob.java`
- Create: `src/main/java/com/gechuang/stationery/payment/WechatPaymentController.java`
- Modify: `src/main/java/com/gechuang/stationery/mainflow/MainFlowService.java`
- Modify: `src/main/java/com/gechuang/stationery/mainflow/MainFlowMapper.java`
- Modify: `src/main/resources/mapper/mainflow/MainFlowMapper.xml`
- Create: `src/test/java/com/gechuang/stationery/payment/WechatPaymentOrderServiceTest.java`
- Create: `src/test/java/com/gechuang/stationery/payment/WechatPaymentControllerTest.java`

**Interfaces:**
- Produces: `POST /api/mini/payments/native-orders`.
- Produces: `GET /api/mini/payments/native-orders/{outTradeNo}`.
- Produces: `POST /api/payments/wechat/notify` with no merchant session requirement.
- Consumes: a trusted `MainFlowService.applyConfirmedWechatRecharge` path with the order's snapshotted recharge/gift amounts and idempotency key `wechat-native:{outTradeNo}`.

- [ ] **Step 1: Write failing orchestration tests**

Cover bookkeeping stores rejecting the real-order endpoint, enabled stores receiving `codeUrl`, order ownership isolation, callback amount/sub-mchid validation, duplicate callback idempotency, query-confirmed success, tier changes after order creation preserving the promised gift amount, failed payment, closed/expired payment, and reverse/refund attempts on real-payment-backed wallet transactions being rejected before wallet mutation.

- [ ] **Step 2: Run the tests and verify they fail**

Run: `mvn -q -Dtest=WechatPaymentOrderServiceTest,WechatPaymentControllerTest test`

Expected: compilation FAIL.

- [ ] **Step 3: Add payment-order mapper operations**

Required operations: insert order, select by store and order number, select for update, update prepay result, atomically claim a throttled active query through `last_query_at`, update terminal state, link the resulting wallet transaction, find an order by wallet transaction, select expired waiting orders, and select successful wallet transaction by idempotency key.

- [ ] **Step 4: Make recharge idempotency return an existing transaction before wallet mutation**

At the start of `MainFlowService.recharge`, if a nonblank idempotency key already exists, verify its store/member/type match and return it. Keep the database unique key as the concurrent safety backstop. Refactor the shared wallet mutation into one internal method so ordinary bookkeeping calculates the current tier, while `applyConfirmedWechatRecharge` applies the exact recharge/gift snapshot promised when the Native order was created.

- [ ] **Step 5: Implement transactional payment success application**

Lock the payment order, verify store, member, `sp_mchid`, `sub_mchid`, AppID, exact paid amount, and status, then call `applyConfirmedWechatRecharge` with payment method `WECHAT` and idempotency key `wechat-native:{outTradeNo}`. Link the resulting wallet transaction and mark the order successful in the same database transaction. Repeated success handling returns the existing transaction.

- [ ] **Step 6: Implement controller endpoints and callback response rules**

The callback endpoint reads the exact raw body. Return 200/204 only after signature verification and durable idempotent processing; return 4xx for invalid signatures and 5xx for retriable persistence failures. Never include callback details in logs. The authenticated query endpoint returns local terminal state immediately and invokes WeChat only for waiting orders whose `last_query_at` throttle has elapsed.

- [ ] **Step 7: Close abandoned orders and protect existing recovery actions**

Run a once-per-minute job that claims expired `WAITING_PAYMENT` orders, requests close when possible, and marks them `CLOSED` without changing wallet balances. Until a real WeChat refund workflow is implemented and accepted during merchant onboarding, existing reverse/refund operations must reject wallet transactions linked to real payment orders; bookkeeping transactions preserve their current behavior.

- [ ] **Step 8: Run payment and transaction regression tests**

Run: `mvn -q -Dtest=WechatPaymentOrderServiceTest,WechatPaymentControllerTest,TransactionRuleTest,MainFlowServiceNotificationTest test`

Expected: PASS.

- [ ] **Step 9: Commit the payment orchestration task**

```bash
git add src/main/java/com/gechuang/stationery/StationeryMemberApplication.java src/main/java/com/gechuang/stationery/payment src/main/java/com/gechuang/stationery/mainflow src/main/resources/mapper/mainflow src/test/java/com/gechuang/stationery/payment
git commit -m "feat: add idempotent native payment workflow"
```

### Task 7: Complete backend verification and production configuration documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-09-21-tencent-cloud-miniprogram-migration-design.md` only if implementation details prove the approved contract inaccurate.

**Interfaces:**
- Produces: exact environment-variable documentation with `BOOKKEEPING` as the copy-safe default.

- [ ] **Step 1: Document both modes without secret values**

Include the complete environment variable names, explain that `BOOKKEEPING` requires no merchant credentials, and state that `WECHAT_SERVICE_PROVIDER` requires service-provider and per-store authorization data. Document that real-payment-backed recharge transactions cannot use the existing ledger-only reverse/refund operations until the merchant-onboarding phase adds and accepts the WeChat refund flow.

- [ ] **Step 2: Run the focused backend suite**

Run: `mvn -q -Dtest=RestResponseTest,TransactionRuleTest,MemberUpdateRequestTest,MainFlowBearerAuthenticationTest,MiniProgramAuthServiceTest,StorePaymentCapabilityServiceTest,WechatGatewayConfigurationTest,WechatPaymentOrderServiceTest,WechatPaymentControllerTest test`

Expected: PASS.

- [ ] **Step 3: Run package verification**

Run: `mvn -q -DskipTests package`

Expected: `target/stationery-member-backend-0.0.1-SNAPSHOT.jar` is created.

- [ ] **Step 4: Scan for secrets and obsolete independent-mini references**

Run: `rg -n "mini_stationery_member|mini_store_|AppSecret|AccessKey Secret|private\\.wx|api-v3-key:[[:space:]]*[^$]" . -g '!target/**'`

Expected: no real secret values and no active independent-mini schema references.

- [ ] **Step 5: Commit the backend documentation and verification task**

```bash
git add README.md
git commit -m "docs: document miniprogram and payment configuration"
```
