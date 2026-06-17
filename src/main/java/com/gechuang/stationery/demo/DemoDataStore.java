package com.gechuang.stationery.demo;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.ErrorCode;
import com.gechuang.stationery.common.PageResult;
import com.gechuang.stationery.recharge.RechargeTierRule;
import com.gechuang.stationery.transaction.RechargeGiftCalculator;
import com.gechuang.stationery.transaction.WalletCalculator;
import com.gechuang.stationery.transaction.WalletSnapshot;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DemoDataStore {

    public static final String MERCHANT_TOKEN = "MERCHANT_TOKEN";
    public static final String ADMIN_TOKEN = "ADMIN_TOKEN";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_DELETED = "DELETED";
    public static final String TX_SUCCESS = "SUCCESS";
    public static final String TX_REVERSED = "REVERSED";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AtomicLong storeId = new AtomicLong(1);
    private final AtomicLong adminId = new AtomicLong(1);
    private final AtomicLong memberId = new AtomicLong(1);
    private final AtomicLong txId = new AtomicLong(1);
    private final AtomicLong campaignId = new AtomicLong(1);
    private final Map<Long, StoreAccount> stores = new LinkedHashMap<>();
    private final Map<Long, AdminUser> admins = new LinkedHashMap<>();
    private final Map<Long, MemberRecord> members = new LinkedHashMap<>();
    private final Map<Long, WalletRecord> wallets = new LinkedHashMap<>();
    private final Map<Long, List<TierRecord>> tiers = new HashMap<>();
    private final Map<Long, TransactionRecord> transactions = new LinkedHashMap<>();
    private final Map<String, Long> idempotencyIndex = new HashMap<>();
    private final Map<String, SessionRecord> sessions = new HashMap<>();
    private final List<Map<String, Object>> auditLogs = new ArrayList<>();
    private final List<Map<String, Object>> campaigns = new ArrayList<>();

    public DemoDataStore() {
        seed();
    }

    public synchronized AuthResult loginMerchant(String account, String password) {
        StoreAccount store = stores.values().stream()
                .filter(item -> Objects.equals(item.account, account))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_401, "账号或密码错误"));
        if (STATUS_DISABLED.equals(store.status)) {
            throw new BusinessException(ErrorCode.AUTH_423, "账号已停用");
        }
        if (!Objects.equals(store.passwordHash, hash(password))) {
            throw new BusinessException(ErrorCode.AUTH_401, "账号或密码错误");
        }
        store.lastLoginAt = LocalDateTime.now();
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new SessionRecord("MERCHANT", store.id, LocalDateTime.now().plusHours(12)));
        return new AuthResult(token, storeMap(store));
    }

    public synchronized AuthResult loginAdmin(String username, String password) {
        AdminUser admin = admins.values().stream()
                .filter(item -> Objects.equals(item.username, username))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_401, "账号或密码错误"));
        if (STATUS_DISABLED.equals(admin.status)) {
            throw new BusinessException(ErrorCode.AUTH_423, "账号已停用");
        }
        if (!Objects.equals(admin.passwordHash, hash(password))) {
            throw new BusinessException(ErrorCode.AUTH_401, "账号或密码错误");
        }
        admin.lastLoginAt = LocalDateTime.now();
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new SessionRecord("ADMIN", admin.id, LocalDateTime.now().plusHours(12)));
        return new AuthResult(token, adminMap(admin));
    }

    public synchronized StoreAccount requireMerchant(HttpServletRequest request) {
        SessionRecord session = requireSession(request, MERCHANT_TOKEN, "MERCHANT");
        StoreAccount store = stores.get(session.principalId);
        if (store == null) {
            throw new BusinessException(ErrorCode.AUTH_401, "登录已过期");
        }
        if (STATUS_DISABLED.equals(store.status)) {
            throw new BusinessException(ErrorCode.AUTH_423, "账号已停用");
        }
        return store;
    }

    public synchronized AdminUser requireAdmin(HttpServletRequest request) {
        SessionRecord session = requireSession(request, ADMIN_TOKEN, "ADMIN");
        AdminUser admin = admins.get(session.principalId);
        if (admin == null || STATUS_DISABLED.equals(admin.status)) {
            throw new BusinessException(ErrorCode.AUTH_401, "登录已过期");
        }
        return admin;
    }

    public synchronized void logout(String token) {
        if (StringUtils.hasText(token)) {
            sessions.remove(token);
        }
    }

    public synchronized Map<String, Object> merchantDashboard(StoreAccount store) {
        List<MemberRecord> storeMembers = activeMembers(store.id);
        BigDecimal totalRecharge = sumTransactions(store.id, "RECHARGE");
        BigDecimal totalGift = transactions.values().stream()
                .filter(tx -> tx.storeId.equals(store.id) && "RECHARGE".equals(tx.type) && TX_SUCCESS.equals(tx.status))
                .map(tx -> tx.giftAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pending = storeMembers.stream()
                .map(member -> wallets.get(member.id).total())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);

        Map<String, Object> result = storeMap(store);
        result.put("updatedAt", format(LocalDateTime.now()));
        result.put("memberCount", storeMembers.size());
        result.put("totalRechargeAmount", money(totalRecharge));
        result.put("pendingConsumptionAmount", money(pending));
        result.put("totalGiftAmount", money(totalGift));
        result.put("today", periodStats(store.id, today, today));
        result.put("week", periodStats(store.id, weekStart, today));
        return result;
    }

    public synchronized Map<String, Object> updateMerchantProfile(StoreAccount store, Map<String, Object> body) {
        String shopName = string(body.get("shopName"));
        if (!StringUtils.hasText(shopName) || shopName.length() > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "店铺名称长度需为 1-50");
        }
        store.shopName = shopName;
        store.avatarUrl = stringOrDefault(body.get("avatarUrl"), store.avatarUrl);
        store.updatedAt = LocalDateTime.now();
        audit("MERCHANT", store.id, "merchant", "updateProfile", store.id, null, storeMap(store));
        return storeMap(store);
    }

    public synchronized void updateMerchantPassword(StoreAccount store, Map<String, Object> body) {
        String newPassword = string(body.get("newPassword"));
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 6 || newPassword.length() > 32) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "密码长度需为 6-32");
        }
        store.passwordHash = hash(newPassword);
        store.updatedAt = LocalDateTime.now();
    }

    public synchronized List<Map<String, Object>> getRechargeTiers(Long storeIdValue) {
        return tiers.getOrDefault(storeIdValue, List.of()).stream()
                .sorted(Comparator.comparingInt(tier -> tier.tierNo))
                .map(this::tierMap)
                .toList();
    }

    public synchronized Map<String, Object> updateRechargeTier(StoreAccount store, int tierNo, Map<String, Object> body) {
        BigDecimal rechargeAmount = decimal(body.get("rechargeAmount"));
        BigDecimal giftAmount = decimal(body.get("giftAmount"));
        if (rechargeAmount.compareTo(BigDecimal.ZERO) <= 0 || giftAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "充值金额必须大于 0，赠送金额不能小于 0");
        }
        List<TierRecord> storeTiers = tiers.get(store.id);
        TierRecord tier = storeTiers.stream()
                .filter(item -> item.tierNo == tierNo)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_404, "档位不存在"));
        BigDecimal oldAmount = tier.rechargeAmount;
        tier.rechargeAmount = rechargeAmount;
        tier.giftAmount = giftAmount;
        tier.updatedAt = LocalDateTime.now();
        if (!isIncreasing(storeTiers)) {
            tier.rechargeAmount = oldAmount;
            throw new BusinessException(ErrorCode.BIZ_409, "充值金额必须按档位递增");
        }
        campaigns.add(campaignMap(store, "充值梯度更新"));
        return tierMap(tier);
    }

    public synchronized List<Map<String, Object>> campaigns(Long storeIdValue) {
        return campaigns.stream()
                .filter(item -> Objects.equals(item.get("storeId"), storeIdValue))
                .toList();
    }

    public synchronized PageResult<Map<String, Object>> merchantMembers(StoreAccount store, String keyword, int pageNo, int pageSize) {
        List<Map<String, Object>> records = activeMembers(store.id).stream()
                .filter(member -> matchesMember(member, keyword))
                .sorted(Comparator.comparing((MemberRecord member) -> member.id).reversed())
                .map(this::memberListMap)
                .toList();
        return page(records, pageNo, pageSize);
    }

    public synchronized Map<String, Object> createMember(StoreAccount store, Map<String, Object> body) {
        String mobile = string(body.get("mobile"));
        if (!validMobile(mobile)) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "手机号格式不正确");
        }
        boolean duplicate = members.values().stream()
                .anyMatch(member -> member.storeId.equals(store.id)
                        && Objects.equals(member.mobile, mobile)
                        && !STATUS_DELETED.equals(member.status));
        if (duplicate) {
            throw new BusinessException(ErrorCode.BIZ_409, "手机号已存在");
        }
        MemberRecord member = new MemberRecord();
        member.id = memberId.getAndIncrement();
        member.storeId = store.id;
        member.memberNo = "M" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + String.format("%04d", member.id);
        applyMemberFields(member, body, true);
        member.status = STATUS_ACTIVE;
        member.joinedAt = LocalDateTime.now();
        member.createdAt = LocalDateTime.now();
        member.updatedAt = LocalDateTime.now();
        members.put(member.id, member);
        wallets.put(member.id, new WalletRecord(member.id));
        return memberDetailMap(member);
    }

    public synchronized Map<String, Object> updateMember(Long storeIdValue, Long id, Map<String, Object> body, boolean admin) {
        MemberRecord member = requireMember(id);
        if (!admin && !member.storeId.equals(storeIdValue)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "会员不存在");
        }
        applyMemberFields(member, body, false);
        member.status = stringOrDefault(body.get("status"), member.status);
        member.updatedAt = LocalDateTime.now();
        return memberDetailMap(member);
    }

    public synchronized void softDeleteMember(Long storeIdValue, Long id, boolean admin) {
        MemberRecord member = requireMember(id);
        if (!admin && !member.storeId.equals(storeIdValue)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "会员不存在");
        }
        member.status = STATUS_DELETED;
        member.deletedAt = LocalDateTime.now();
        member.updatedAt = LocalDateTime.now();
    }

    public synchronized Map<String, Object> memberDetail(Long storeIdValue, Long id, boolean admin) {
        MemberRecord member = requireMember(id);
        if (!admin && !member.storeId.equals(storeIdValue)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "会员不存在");
        }
        return memberDetailMap(member);
    }

    public synchronized PageResult<Map<String, Object>> memberTransactions(Long storeIdValue, Long memberIdValue, String type, int pageNo, int pageSize) {
        requireMember(memberIdValue);
        List<Map<String, Object>> records = transactions.values().stream()
                .filter(tx -> tx.memberId.equals(memberIdValue))
                .filter(tx -> storeIdValue == null || tx.storeId.equals(storeIdValue))
                .filter(tx -> !StringUtils.hasText(type) || Objects.equals(tx.type, type))
                .sorted(Comparator.comparing((TransactionRecord tx) -> tx.createdAt).reversed())
                .map(this::transactionMap)
                .toList();
        return page(records, pageNo, pageSize);
    }

    public synchronized Map<String, Object> lookupMember(StoreAccount store, String keyword) {
        List<MemberRecord> candidates = activeMembers(store.id).stream()
                .filter(member -> {
                    if (keyword == null) {
                        return false;
                    }
                    if (keyword.length() == 11) {
                        return Objects.equals(member.mobile, keyword);
                    }
                    return member.mobile.endsWith(keyword);
                })
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        if (candidates.isEmpty()) {
            result.put("matchType", "NONE");
        } else if (keyword.length() == 11) {
            result.put("matchType", "EXACT");
            result.put("member", memberListMap(candidates.get(0)));
        } else if (candidates.size() == 1) {
            result.put("matchType", "SUFFIX_SINGLE");
            result.put("member", memberListMap(candidates.get(0)));
        } else {
            result.put("matchType", "SUFFIX_MULTIPLE");
            result.put("candidates", candidates.stream().map(this::memberListMap).toList());
        }
        return result;
    }

    public synchronized Map<String, Object> recharge(StoreAccount store, Map<String, Object> body) {
        String idempotencyKey = string(body.get("idempotencyKey"));
        if (StringUtils.hasText(idempotencyKey) && idempotencyIndex.containsKey(idempotencyKey)) {
            return transactionMap(requireTransaction(idempotencyIndex.get(idempotencyKey)));
        }
        MemberRecord member = requireTradableMember(store.id, longValue(body.get("memberId")));
        BigDecimal amount = decimal(body.get("amount"));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "充值金额必须大于 0");
        }
        BigDecimal gift = RechargeGiftCalculator.calculate(amount, tierRules(store.id));
        WalletRecord wallet = wallets.get(member.id);
        WalletSnapshot before = wallet.snapshot();
        wallet.rechargeBalance = wallet.rechargeBalance.add(amount).setScale(2);
        wallet.giftBalance = wallet.giftBalance.add(gift).setScale(2);
        wallet.accumulatedRecharge = wallet.accumulatedRecharge.add(amount).setScale(2);
        wallet.accumulatedGift = wallet.accumulatedGift.add(gift).setScale(2);
        wallet.rechargeCount++;
        wallet.version++;
        TransactionRecord tx = createTransaction(store.id, member.id, null, "RECHARGE", amount, gift, before,
                wallet.snapshot(), stringOrDefault(body.get("paymentMethod"), "CASH"),
                stringOrDefault(body.get("itemName"), "会员充值"), string(body.get("remark")), "MERCHANT", store.id);
        rememberIdempotency(idempotencyKey, tx.id);
        return transactionMap(tx);
    }

    public synchronized Map<String, Object> consume(StoreAccount store, Map<String, Object> body) {
        String idempotencyKey = string(body.get("idempotencyKey"));
        if (StringUtils.hasText(idempotencyKey) && idempotencyIndex.containsKey(idempotencyKey)) {
            return transactionMap(requireTransaction(idempotencyIndex.get(idempotencyKey)));
        }
        MemberRecord member = requireTradableMember(store.id, longValue(body.get("memberId")));
        BigDecimal amount = decimal(body.get("amount"));
        WalletRecord wallet = wallets.get(member.id);
        WalletSnapshot before = wallet.snapshot();
        WalletSnapshot after = WalletCalculator.consume(before, amount);
        wallet.rechargeBalance = after.rechargeBalance();
        wallet.giftBalance = after.giftBalance();
        wallet.accumulatedConsumption = wallet.accumulatedConsumption.add(amount).setScale(2);
        wallet.consumptionCount++;
        wallet.version++;
        TransactionRecord tx = createTransaction(store.id, member.id, null, "CONSUMPTION", amount, BigDecimal.ZERO,
                before, after, stringOrDefault(body.get("paymentMethod"), "CASH"),
                stringOrDefault(body.get("itemName"), "消费扣款"), string(body.get("remark")), "MERCHANT", store.id);
        rememberIdempotency(idempotencyKey, tx.id);
        return transactionMap(tx);
    }

    public synchronized List<Map<String, Object>> recentTransactions(StoreAccount store, String type, int limit) {
        return transactions.values().stream()
                .filter(tx -> tx.storeId.equals(store.id))
                .filter(tx -> !StringUtils.hasText(type) || Objects.equals(tx.type, type))
                .sorted(Comparator.comparing((TransactionRecord tx) -> tx.createdAt).reversed())
                .limit(Math.max(1, limit))
                .map(this::transactionMap)
                .toList();
    }

    public synchronized Map<String, Object> reverse(Long storeIdValue, Long txIdValue, boolean admin, String operatorType, Long operatorId) {
        TransactionRecord tx = requireTransaction(txIdValue);
        if (!admin && !tx.storeId.equals(storeIdValue)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "流水不存在");
        }
        if (!TX_SUCCESS.equals(tx.status)) {
            throw new BusinessException(ErrorCode.BIZ_409, "流水不可撤销");
        }
        if ("RECHARGE".equals(tx.type)) {
            return reverseRecharge(tx, operatorType, operatorId);
        }
        if ("CONSUMPTION".equals(tx.type)) {
            return refund(tx.storeId, tx.id, tx.amount, operatorType, operatorId);
        }
        throw new BusinessException(ErrorCode.BIZ_409, "流水不可撤销");
    }

    public synchronized Map<String, Object> refund(Long storeIdValue, Long txIdValue, BigDecimal refundAmount,
                                                   String operatorType, Long operatorId) {
        TransactionRecord source = requireTransaction(txIdValue);
        if (!source.storeId.equals(storeIdValue) || !"CONSUMPTION".equals(source.type)) {
            throw new BusinessException(ErrorCode.BIZ_409, "仅消费流水可退款");
        }
        BigDecimal refunded = transactions.values().stream()
                .filter(tx -> Objects.equals(tx.originalTransactionId, source.id))
                .filter(tx -> "CONSUMPTION_REFUND".equals(tx.type))
                .map(tx -> tx.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (source.amount.subtract(refunded).compareTo(refundAmount) < 0) {
            throw new BusinessException(ErrorCode.BIZ_409, "退款金额超过可退金额");
        }
        WalletRecord wallet = wallets.get(source.memberId);
        WalletSnapshot before = wallet.snapshot();
        wallet.rechargeBalance = wallet.rechargeBalance.add(refundAmount).setScale(2);
        wallet.accumulatedConsumption = wallet.accumulatedConsumption.subtract(refundAmount).max(BigDecimal.ZERO).setScale(2);
        wallet.version++;
        TransactionRecord refund = createTransaction(source.storeId, source.memberId, source.id, "CONSUMPTION_REFUND",
                refundAmount, BigDecimal.ZERO, before, wallet.snapshot(), source.paymentMethod,
                "消费退款", "退款", operatorType, operatorId);
        return transactionMap(refund);
    }

    public synchronized Map<String, Object> correction(Long memberIdValue, String correctionType, BigDecimal amount,
                                                       String reason, Long adminIdValue) {
        MemberRecord member = requireMember(memberIdValue);
        WalletRecord wallet = wallets.get(member.id);
        WalletSnapshot before = wallet.snapshot();
        switch (correctionType) {
            case "ADD_RECHARGE" -> wallet.rechargeBalance = wallet.rechargeBalance.add(amount).setScale(2);
            case "ADD_GIFT" -> wallet.giftBalance = wallet.giftBalance.add(amount).setScale(2);
            case "DEDUCT_RECHARGE" -> wallet.rechargeBalance = wallet.rechargeBalance.subtract(amount).max(BigDecimal.ZERO).setScale(2);
            case "DEDUCT_GIFT" -> wallet.giftBalance = wallet.giftBalance.subtract(amount).max(BigDecimal.ZERO).setScale(2);
            default -> throw new BusinessException(ErrorCode.VALIDATION_422, "冲正类型不正确");
        }
        wallet.version++;
        TransactionRecord tx = createTransaction(member.storeId, member.id, null, "MANUAL_CORRECTION", amount,
                BigDecimal.ZERO, before, wallet.snapshot(), "OTHER", "人工冲正", reason, "ADMIN", adminIdValue);
        return transactionMap(tx);
    }

    public synchronized Map<String, Object> adminOverview() {
        List<Map<String, Object>> storeRows = stores.values().stream().map(store -> {
            BigDecimal totalRecharge = sumTransactions(store.id, "RECHARGE");
            BigDecimal pending = activeMembers(store.id).stream()
                    .map(member -> wallets.get(member.id).total())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> row = storeMap(store);
            row.put("totalRechargeAmount", money(totalRecharge));
            row.put("pendingConsumptionAmount", money(pending));
            return row;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("storeCount", stores.size());
        result.put("activeStoreCount", stores.values().stream().filter(store -> STATUS_ACTIVE.equals(store.status)).count());
        result.put("totalRechargeAmount", money(transactions.values().stream()
                .filter(tx -> "RECHARGE".equals(tx.type) && TX_SUCCESS.equals(tx.status))
                .map(tx -> tx.amount).reduce(BigDecimal.ZERO, BigDecimal::add)));
        result.put("pendingConsumptionAmount", money(members.values().stream()
                .filter(member -> !STATUS_DELETED.equals(member.status))
                .map(member -> wallets.get(member.id).total())
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        result.put("stores", storeRows);
        return result;
    }

    public synchronized PageResult<Map<String, Object>> adminStores(String keyword, String status, int pageNo, int pageSize) {
        List<Map<String, Object>> rows = stores.values().stream()
                .filter(store -> !StringUtils.hasText(keyword)
                        || store.shopName.contains(keyword)
                        || store.account.contains(keyword)
                        || store.contactMobile.contains(keyword))
                .filter(store -> !StringUtils.hasText(status) || Objects.equals(store.status, status))
                .map(this::storeMap)
                .toList();
        return page(rows, pageNo, pageSize);
    }

    public synchronized Map<String, Object> createStore(Map<String, Object> body) {
        String account = string(body.get("account"));
        if (!StringUtils.hasText(account)) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "商家账号不能为空");
        }
        boolean exists = stores.values().stream().anyMatch(store -> Objects.equals(store.account, account));
        if (exists) {
            throw new BusinessException(ErrorCode.BIZ_409, "商家账号已存在");
        }
        StoreAccount store = new StoreAccount();
        store.id = storeId.getAndIncrement();
        store.account = account;
        store.passwordHash = hash(stringOrDefault(body.get("initialPassword"), "123456"));
        store.shopName = stringOrDefault(body.get("shopName"), account);
        store.contactMobile = stringOrDefault(body.get("contactMobile"), "13800000000");
        store.address = string(body.get("address"));
        store.avatarUrl = string(body.get("avatarUrl"));
        store.status = stringOrDefault(body.get("status"), STATUS_ACTIVE);
        store.createdAt = LocalDateTime.now();
        store.updatedAt = LocalDateTime.now();
        stores.put(store.id, store);
        seedTiers(store.id);
        return storeMap(store);
    }

    public synchronized Map<String, Object> updateStore(Long id, Map<String, Object> body) {
        StoreAccount store = stores.get(id);
        if (store == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "门店不存在");
        }
        store.shopName = stringOrDefault(body.get("shopName"), store.shopName);
        store.contactMobile = stringOrDefault(body.get("contactMobile"), store.contactMobile);
        store.address = stringOrDefault(body.get("address"), store.address);
        store.avatarUrl = stringOrDefault(body.get("avatarUrl"), store.avatarUrl);
        store.status = stringOrDefault(body.get("status"), store.status);
        store.updatedAt = LocalDateTime.now();
        return storeMap(store);
    }

    public synchronized Map<String, Object> updateStoreStatus(Long id, String status) {
        StoreAccount store = stores.get(id);
        if (store == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "门店不存在");
        }
        store.status = status;
        store.updatedAt = LocalDateTime.now();
        return storeMap(store);
    }

    public synchronized void resetStorePassword(Long id, String newPassword) {
        StoreAccount store = stores.get(id);
        if (store == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "门店不存在");
        }
        store.passwordHash = hash(newPassword);
    }

    public synchronized PageResult<Map<String, Object>> adminMembers(String mobile, String name, Long storeIdValue,
                                                                      BigDecimal rechargeMin, BigDecimal rechargeMax,
                                                                      BigDecimal consumptionMin, BigDecimal consumptionMax,
                                                                      int pageNo, int pageSize) {
        List<Map<String, Object>> rows = members.values().stream()
                .filter(member -> !STATUS_DELETED.equals(member.status))
                .filter(member -> storeIdValue == null || Objects.equals(member.storeId, storeIdValue))
                .filter(member -> !StringUtils.hasText(mobile) || member.mobile.contains(mobile))
                .filter(member -> !StringUtils.hasText(name) || member.name.contains(name))
                .filter(member -> between(wallets.get(member.id).accumulatedRecharge, rechargeMin, rechargeMax))
                .filter(member -> between(wallets.get(member.id).accumulatedConsumption, consumptionMin, consumptionMax))
                .map(this::adminMemberMap)
                .toList();
        return page(rows, pageNo, pageSize);
    }

    public synchronized List<Map<String, Object>> adminMembersForExport(String mobile, String name, Long storeIdValue,
                                                                         BigDecimal rechargeMin, BigDecimal rechargeMax,
                                                                         BigDecimal consumptionMin, BigDecimal consumptionMax) {
        return adminMembers(mobile, name, storeIdValue, rechargeMin, rechargeMax,
                consumptionMin, consumptionMax, 1, 5001).getRecords();
    }

    public synchronized List<Map<String, Object>> activityRows() {
        return stores.values().stream().map(store -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("storeName", store.shopName);
            row.put("address", store.address);
            row.put("avgUsageMinutes", store.id % 2 == 0 ? 214 : 286);
            row.put("openCount", store.id % 2 == 0 ? 36 : 42);
            row.put("avgStayMinutes", store.id % 2 == 0 ? 256 : 318);
            row.put("activityStatus", store.id % 4 == 0 ? "LOW_ACTIVE" : "ACTIVE");
            row.put("statDate", LocalDate.now().minusDays(1).toString());
            return row;
        }).toList();
    }

    public synchronized Map<String, Object> currentStoreMap(StoreAccount store) {
        return storeMap(store);
    }

    public synchronized Map<String, Object> currentAdminMap(AdminUser admin) {
        return adminMap(admin);
    }

    public String tokenFromCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (Objects.equals(cookie.getName(), cookieName)) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void seed() {
        AdminUser admin = new AdminUser();
        admin.id = adminId.getAndIncrement();
        admin.username = "superadmin";
        admin.passwordHash = hash("Member@123");
        admin.displayName = "Admin";
        admin.role = "SUPER_ADMIN";
        admin.status = STATUS_ACTIVE;
        admin.createdAt = LocalDateTime.now();
        admins.put(admin.id, admin);

        StoreAccount xinyue = seedStore("xinyue_store", "欣悦生活馆", "13800138000", STATUS_ACTIVE, "北京市朝阳区建国路88号");
        seedStore("east_store", "悦享生活馆·东城店", "13800138011", STATUS_ACTIVE, "北京市朝阳区建国路88号");
        seedStore("west_store", "悦享生活馆·西城店", "13800138012", STATUS_ACTIVE, "北京市西城区金融大街12号");
        seedStore("closed_store", "悦享生活馆·朝阳门店", "13800138013", STATUS_DISABLED, "北京市东城区朝阳门内大街18号");
        for (int i = 1; i <= 8; i++) {
            seedStore(
                    String.format("demo_store_%02d", i),
                    String.format("悦享生活馆·演示%02d店", i),
                    String.format("138001381%02d", i),
                    i % 4 == 0 ? STATUS_DISABLED : STATUS_ACTIVE,
                    String.format("北京市海淀区演示路%d号", i)
            );
        }

        MemberRecord zhao = seedMember(xinyue.id, "赵天宇", "13600136004", "MALE", 41, "435.00", "100.00", STATUS_ACTIVE);
        MemberRecord li = seedMember(xinyue.id, "李小晴", "13800138001", "FEMALE", 28, "50.00", "10.00", STATUS_ACTIVE);
        seedMember(xinyue.id, "吴佳宁", "13300133007", "FEMALE", 45, "170.00", "13.80", STATUS_ACTIVE);
        seedMember(xinyue.id, "后四位同号A", "13900138001", "MALE", 33, "80.00", "20.00", STATUS_ACTIVE);
        seedMember(xinyue.id, "余额不足会员", "13700137009", "FEMALE", 22, "0.00", "0.00", STATUS_ACTIVE);
        seedMember(xinyue.id, "已删除会员", "13500135009", "MALE", 30, "0.00", "0.00", STATUS_DELETED);
        for (int i = 1; i <= 8; i++) {
            seedMember(
                    xinyue.id,
                    String.format("演示会员%02d", i),
                    String.format("13900001%03d", i),
                    i % 2 == 0 ? "MALE" : "FEMALE",
                    20 + i,
                    String.valueOf(40 + i * 15) + ".00",
                    String.valueOf(i * 3) + ".00",
                    STATUS_ACTIVE
            );
        }

        createSeedTx(xinyue.id, zhao.id, "RECHARGE", new BigDecimal("500.00"), new BigDecimal("100.00"), "RC202606010001");
        createSeedTx(xinyue.id, zhao.id, "CONSUMPTION", new BigDecimal("65.00"), BigDecimal.ZERO, "CP202606010001");
        for (int i = 2; i <= 7; i++) {
            createSeedTx(xinyue.id, zhao.id, "RECHARGE", new BigDecimal("100.00"), new BigDecimal("10.00"),
                    String.format("RC202606%02d0001", i));
            createSeedTx(xinyue.id, zhao.id, "CONSUMPTION", new BigDecimal("20.00"), BigDecimal.ZERO,
                    String.format("CP202606%02d0001", i));
        }
        createSeedTx(xinyue.id, li.id, "RECHARGE", new BigDecimal("100.00"), new BigDecimal("10.00"), "RC202606050001");
        createSeedTx(xinyue.id, li.id, "CONSUMPTION", new BigDecimal("50.00"), BigDecimal.ZERO, "CP202606070001");
    }

    private StoreAccount seedStore(String account, String shopName, String mobile, String status, String address) {
        StoreAccount store = new StoreAccount();
        store.id = storeId.getAndIncrement();
        store.account = account;
        store.passwordHash = hash("123456");
        store.shopName = shopName;
        store.contactMobile = mobile;
        store.status = status;
        store.address = address;
        store.avatarUrl = "";
        store.createdAt = LocalDateTime.now();
        store.updatedAt = LocalDateTime.now();
        stores.put(store.id, store);
        seedTiers(store.id);
        return store;
    }

    private void seedTiers(Long storeIdValue) {
        tiers.put(storeIdValue, new ArrayList<>(List.of(
                new TierRecord(1, new BigDecimal("100.00"), new BigDecimal("10.00")),
                new TierRecord(2, new BigDecimal("200.00"), new BigDecimal("20.00")),
                new TierRecord(3, new BigDecimal("300.00"), new BigDecimal("35.00")),
                new TierRecord(4, new BigDecimal("500.00"), new BigDecimal("100.00"))
        )));
    }

    private MemberRecord seedMember(Long storeIdValue, String name, String mobile, String gender, int age,
                                    String recharge, String gift, String status) {
        MemberRecord member = new MemberRecord();
        member.id = memberId.getAndIncrement();
        member.storeId = storeIdValue;
        member.memberNo = "M20260616" + String.format("%04d", member.id);
        member.name = name;
        member.mobile = mobile;
        member.gender = gender;
        member.age = age;
        member.status = status;
        member.joinedAt = LocalDateTime.now().minusDays(member.id);
        member.createdAt = member.joinedAt;
        member.updatedAt = member.joinedAt;
        members.put(member.id, member);
        WalletRecord wallet = new WalletRecord(member.id);
        wallet.rechargeBalance = new BigDecimal(recharge).setScale(2);
        wallet.giftBalance = new BigDecimal(gift).setScale(2);
        wallets.put(member.id, wallet);
        return member;
    }

    private void createSeedTx(Long storeIdValue, Long memberIdValue, String type, BigDecimal amount, BigDecimal gift, String serialNo) {
        WalletRecord wallet = wallets.get(memberIdValue);
        TransactionRecord tx = new TransactionRecord();
        tx.id = txId.getAndIncrement();
        tx.serialNo = serialNo;
        tx.storeId = storeIdValue;
        tx.memberId = memberIdValue;
        tx.type = type;
        tx.status = TX_SUCCESS;
        tx.amount = amount.setScale(2);
        tx.giftAmount = gift.setScale(2);
        tx.beforeRechargeBalance = wallet.rechargeBalance;
        tx.beforeGiftBalance = wallet.giftBalance;
        tx.beforeTotalBalance = wallet.total();
        tx.afterRechargeBalance = wallet.rechargeBalance;
        tx.afterGiftBalance = wallet.giftBalance;
        tx.afterTotalBalance = wallet.total();
        tx.paymentMethod = "CASH";
        tx.orderNo = serialNo;
        tx.itemName = "RECHARGE".equals(type) ? "会员充值" : "消费扣款";
        tx.operatorType = "MERCHANT";
        tx.operatorId = storeIdValue;
        tx.createdAt = LocalDateTime.now().minusDays(tx.id);
        transactions.put(tx.id, tx);
        if ("RECHARGE".equals(type)) {
            wallet.accumulatedRecharge = wallet.accumulatedRecharge.add(tx.amount).setScale(2);
            wallet.accumulatedGift = wallet.accumulatedGift.add(tx.giftAmount).setScale(2);
            wallet.rechargeCount++;
        } else if ("CONSUMPTION".equals(type)) {
            wallet.accumulatedConsumption = wallet.accumulatedConsumption.add(tx.amount).setScale(2);
            wallet.consumptionCount++;
        }
    }

    private Map<String, Object> reverseRecharge(TransactionRecord source, String operatorType, Long operatorId) {
        WalletRecord wallet = wallets.get(source.memberId);
        BigDecimal totalReturn = source.amount.add(source.giftAmount);
        if (wallet.total().compareTo(totalReturn) < 0) {
            throw new BusinessException(ErrorCode.BIZ_409, "余额不足，无法撤销充值");
        }
        WalletSnapshot before = wallet.snapshot();
        WalletSnapshot after = WalletCalculator.deduct(before, totalReturn);
        wallet.rechargeBalance = after.rechargeBalance();
        wallet.giftBalance = after.giftBalance();
        wallet.accumulatedRecharge = wallet.accumulatedRecharge.subtract(source.amount).max(BigDecimal.ZERO).setScale(2);
        wallet.accumulatedGift = wallet.accumulatedGift.subtract(source.giftAmount).max(BigDecimal.ZERO).setScale(2);
        wallet.version++;
        source.status = TX_REVERSED;
        source.reversedAt = LocalDateTime.now();
        TransactionRecord reverse = createTransaction(source.storeId, source.memberId, source.id, "RECHARGE_REVERSAL",
                source.amount, source.giftAmount, before, wallet.snapshot(), source.paymentMethod,
                "充值撤销", "撤销充值", operatorType, operatorId);
        return transactionMap(reverse);
    }

    private TransactionRecord createTransaction(Long storeIdValue, Long memberIdValue, Long originalId, String type,
                                                BigDecimal amount, BigDecimal gift, WalletSnapshot before,
                                                WalletSnapshot after, String paymentMethod, String itemName,
                                                String remark, String operatorType, Long operatorId) {
        TransactionRecord tx = new TransactionRecord();
        tx.id = txId.getAndIncrement();
        tx.serialNo = prefix(type) + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", tx.id);
        tx.storeId = storeIdValue;
        tx.memberId = memberIdValue;
        tx.originalTransactionId = originalId;
        tx.type = type;
        tx.status = TX_SUCCESS;
        tx.amount = amount.setScale(2);
        tx.giftAmount = gift.setScale(2);
        tx.beforeRechargeBalance = before.rechargeBalance();
        tx.beforeGiftBalance = before.giftBalance();
        tx.beforeTotalBalance = before.totalBalance();
        tx.afterRechargeBalance = after.rechargeBalance();
        tx.afterGiftBalance = after.giftBalance();
        tx.afterTotalBalance = after.totalBalance();
        tx.paymentMethod = paymentMethod;
        tx.orderNo = tx.serialNo;
        tx.itemName = itemName;
        tx.remark = remark;
        tx.operatorType = operatorType;
        tx.operatorId = operatorId;
        tx.createdAt = LocalDateTime.now();
        transactions.put(tx.id, tx);
        audit(operatorType, operatorId, "transaction", type, tx.id, null, transactionMap(tx));
        return tx;
    }

    private void rememberIdempotency(String idempotencyKey, Long transactionId) {
        if (StringUtils.hasText(idempotencyKey)) {
            idempotencyIndex.put(idempotencyKey, transactionId);
        }
    }

    private SessionRecord requireSession(HttpServletRequest request, String cookieName, String principalType) {
        String token = tokenFromCookie(request, cookieName);
        SessionRecord session = StringUtils.hasText(token) ? sessions.get(token) : null;
        if (session == null || !Objects.equals(session.principalType, principalType)
                || session.expiresAt.isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.AUTH_401, "未登录或登录已过期");
        }
        return session;
    }

    private MemberRecord requireTradableMember(Long storeIdValue, Long memberIdValue) {
        MemberRecord member = requireMember(memberIdValue);
        if (!member.storeId.equals(storeIdValue) || STATUS_DELETED.equals(member.status)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "会员不存在");
        }
        if (STATUS_DISABLED.equals(member.status)) {
            throw new BusinessException(ErrorCode.BIZ_409, "会员已停用");
        }
        return member;
    }

    private MemberRecord requireMember(Long id) {
        MemberRecord member = members.get(id);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "会员不存在");
        }
        return member;
    }

    private TransactionRecord requireTransaction(Long id) {
        TransactionRecord tx = transactions.get(id);
        if (tx == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "流水不存在");
        }
        return tx;
    }

    private void applyMemberFields(MemberRecord member, Map<String, Object> body, boolean create) {
        String mobile = string(body.get("mobile"));
        if (create || StringUtils.hasText(mobile)) {
            if (!validMobile(mobile)) {
                throw new BusinessException(ErrorCode.VALIDATION_422, "手机号格式不正确");
            }
            member.mobile = mobile;
        }
        member.name = stringOrDefault(body.get("name"), member.name);
        member.gender = stringOrDefault(body.get("gender"), member.gender == null ? "MALE" : member.gender);
        member.age = intValue(body.get("age"), member.age == null ? 1 : member.age);
        member.avatarUrl = stringOrDefault(body.get("avatarUrl"), member.avatarUrl);
    }

    private List<MemberRecord> activeMembers(Long storeIdValue) {
        return members.values().stream()
                .filter(member -> member.storeId.equals(storeIdValue))
                .filter(member -> !STATUS_DELETED.equals(member.status))
                .toList();
    }

    private List<RechargeTierRule> tierRules(Long storeIdValue) {
        return tiers.getOrDefault(storeIdValue, List.of()).stream()
                .map(tier -> new RechargeTierRule(tier.tierNo, tier.rechargeAmount, tier.giftAmount))
                .toList();
    }

    private boolean isIncreasing(List<TierRecord> list) {
        List<TierRecord> sorted = list.stream().sorted(Comparator.comparingInt(tier -> tier.tierNo)).toList();
        BigDecimal prev = BigDecimal.ZERO;
        for (TierRecord tier : sorted) {
            if (tier.rechargeAmount.compareTo(prev) <= 0) {
                return false;
            }
            prev = tier.rechargeAmount;
        }
        return true;
    }

    private Map<String, Object> periodStats(Long storeIdValue, LocalDate start, LocalDate end) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("newMembers", members.values().stream()
                .filter(member -> member.storeId.equals(storeIdValue))
                .filter(member -> !member.joinedAt.toLocalDate().isBefore(start) && !member.joinedAt.toLocalDate().isAfter(end))
                .count());
        map.put("consumptionAmount", money(sumPeriod(storeIdValue, "CONSUMPTION", start, end)));
        map.put("rechargeAmount", money(sumPeriod(storeIdValue, "RECHARGE", start, end)));
        return map;
    }

    private BigDecimal sumPeriod(Long storeIdValue, String type, LocalDate start, LocalDate end) {
        return transactions.values().stream()
                .filter(tx -> tx.storeId.equals(storeIdValue) && Objects.equals(tx.type, type) && TX_SUCCESS.equals(tx.status))
                .filter(tx -> !tx.createdAt.toLocalDate().isBefore(start) && !tx.createdAt.toLocalDate().isAfter(end))
                .map(tx -> tx.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumTransactions(Long storeIdValue, String type) {
        return transactions.values().stream()
                .filter(tx -> tx.storeId.equals(storeIdValue) && Objects.equals(tx.type, type) && TX_SUCCESS.equals(tx.status))
                .map(tx -> tx.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean between(BigDecimal value, BigDecimal min, BigDecimal max) {
        return (min == null || value.compareTo(min) >= 0) && (max == null || value.compareTo(max) <= 0);
    }

    private boolean matchesMember(MemberRecord member, String keyword) {
        return !StringUtils.hasText(keyword)
                || member.name.contains(keyword)
                || member.mobile.contains(keyword)
                || member.memberNo.contains(keyword);
    }

    private <T> PageResult<T> page(List<T> source, int pageNo, int pageSize) {
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, pageSize);
        int from = Math.min(source.size(), (safePageNo - 1) * safePageSize);
        int to = Math.min(source.size(), from + safePageSize);
        return new PageResult<>(source.subList(from, to), source.size(), safePageNo, safePageSize);
    }

    private Map<String, Object> storeMap(StoreAccount store) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", store.id);
        map.put("account", store.account);
        map.put("shopName", store.shopName);
        map.put("contactMobile", store.contactMobile);
        map.put("avatarUrl", store.avatarUrl);
        map.put("address", store.address);
        map.put("status", store.status);
        map.put("lastLoginAt", format(store.lastLoginAt));
        map.put("createdAt", format(store.createdAt));
        return map;
    }

    private Map<String, Object> adminMap(AdminUser admin) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", admin.id);
        map.put("username", admin.username);
        map.put("displayName", admin.displayName);
        map.put("role", admin.role);
        map.put("roleLabel", roleLabel(admin.role));
        map.put("avatarUrl", admin.avatarUrl);
        map.put("status", admin.status);
        return map;
    }

    private Map<String, Object> memberListMap(MemberRecord member) {
        WalletRecord wallet = wallets.get(member.id);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", member.id);
        map.put("storeId", member.storeId);
        map.put("storeName", stores.get(member.storeId).shopName);
        map.put("memberNo", member.memberNo);
        map.put("name", member.name);
        map.put("mobile", member.mobile);
        map.put("gender", member.gender);
        map.put("age", member.age);
        map.put("avatarUrl", member.avatarUrl);
        map.put("status", member.status);
        map.put("availableBalance", money(wallet.total()));
        map.put("totalBalance", money(wallet.total()));
        map.put("joinedAt", format(member.joinedAt));
        return map;
    }

    private Map<String, Object> adminMemberMap(MemberRecord member) {
        Map<String, Object> map = memberListMap(member);
        WalletRecord wallet = wallets.get(member.id);
        map.put("totalBalance", money(wallet.total()));
        map.put("accumulatedRecharge", money(wallet.accumulatedRecharge));
        map.put("accumulatedConsumption", money(wallet.accumulatedConsumption));
        return map;
    }

    private Map<String, Object> memberDetailMap(MemberRecord member) {
        Map<String, Object> map = memberListMap(member);
        WalletRecord wallet = wallets.get(member.id);
        map.put("rechargeBalance", money(wallet.rechargeBalance));
        map.put("giftBalance", money(wallet.giftBalance));
        map.put("totalBalance", money(wallet.total()));
        map.put("accumulatedRecharge", money(wallet.accumulatedRecharge));
        map.put("accumulatedGift", money(wallet.accumulatedGift));
        map.put("accumulatedConsumption", money(wallet.accumulatedConsumption));
        map.put("rechargeCount", wallet.rechargeCount);
        map.put("consumptionCount", wallet.consumptionCount);
        return map;
    }

    private Map<String, Object> tierMap(TierRecord tier) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tierNo", tier.tierNo);
        map.put("rechargeAmount", money(tier.rechargeAmount));
        map.put("giftAmount", money(tier.giftAmount));
        map.put("updatedAt", format(tier.updatedAt));
        return map;
    }

    private String roleLabel(String role) {
        if ("SUPER_ADMIN".equals(role)) {
            return "系统管理员";
        }
        return role;
    }

    private Map<String, Object> transactionMap(TransactionRecord tx) {
        MemberRecord member = members.get(tx.memberId);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", tx.id);
        map.put("serialNo", tx.serialNo);
        map.put("storeId", tx.storeId);
        map.put("storeName", stores.get(tx.storeId).shopName);
        map.put("memberId", tx.memberId);
        map.put("memberName", member == null ? "" : member.name);
        map.put("mobile", member == null ? "" : member.mobile);
        map.put("originalTransactionId", tx.originalTransactionId);
        map.put("type", tx.type);
        map.put("status", tx.status);
        map.put("amount", money(tx.amount));
        map.put("giftAmount", money(tx.giftAmount));
        map.put("beforeTotalBalance", money(tx.beforeTotalBalance));
        map.put("afterTotalBalance", money(tx.afterTotalBalance));
        map.put("paymentMethod", tx.paymentMethod);
        map.put("orderNo", tx.orderNo);
        map.put("itemName", tx.itemName);
        map.put("remark", tx.remark);
        map.put("createdAt", format(tx.createdAt));
        return map;
    }

    private Map<String, Object> campaignMap(StoreAccount store, String name) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", campaignId.getAndIncrement());
        map.put("storeId", store.id);
        map.put("storeName", store.shopName);
        map.put("campaignName", name);
        map.put("status", "SAVED");
        map.put("summary", tiers.get(store.id).stream()
                .sorted(Comparator.comparingInt(tier -> tier.tierNo))
                .map(tier -> "充" + money(tier.rechargeAmount) + "送" + money(tier.giftAmount))
                .collect(Collectors.joining("，")));
        map.put("createdAt", format(LocalDateTime.now()));
        return map;
    }

    private void audit(String operatorType, Long operatorId, String module, String action, Long targetId,
                       Object before, Object after) {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("operatorType", operatorType);
        log.put("operatorId", operatorId);
        log.put("module", module);
        log.put("action", action);
        log.put("targetId", targetId);
        log.put("before", before);
        log.put("after", after);
        log.put("createdAt", format(LocalDateTime.now()));
        auditLogs.add(log);
    }

    private String prefix(String type) {
        return switch (type) {
            case "RECHARGE" -> "RC";
            case "CONSUMPTION" -> "CP";
            case "CONSUMPTION_REFUND" -> "RF";
            case "RECHARGE_REVERSAL" -> "RV";
            case "MANUAL_CORRECTION" -> "CR";
            default -> "TX";
        };
    }

    private boolean validMobile(String mobile) {
        return mobile != null && mobile.matches("^1[3-9]\\d{9}$");
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String money(BigDecimal amount) {
        return amount == null ? "0.00" : amount.setScale(2).toPlainString();
    }

    private BigDecimal decimal(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return BigDecimal.ZERO.setScale(2);
        }
        return new BigDecimal(String.valueOf(value)).setScale(2);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String stringOrDefault(Object value, String fallback) {
        String result = string(value);
        return StringUtils.hasText(result) ? result : fallback;
    }

    private String format(LocalDateTime time) {
        return time == null ? null : time.format(TIME);
    }

    public record AuthResult(String token, Map<String, Object> data) {
    }

    public static class StoreAccount {
        Long id;
        String account;
        String passwordHash;
        String shopName;
        String contactMobile;
        String avatarUrl;
        String address;
        String status;
        LocalDateTime lastLoginAt;
        LocalDateTime createdAt;
        LocalDateTime updatedAt;

        public Long getId() {
            return id;
        }
    }

    public static class AdminUser {
        Long id;
        String username;
        String passwordHash;
        String displayName;
        String role;
        String avatarUrl;
        String status;
        LocalDateTime lastLoginAt;
        LocalDateTime createdAt;

        public Long getId() {
            return id;
        }
    }

    private record SessionRecord(String principalType, Long principalId, LocalDateTime expiresAt) {
    }

    private static class MemberRecord {
        Long id;
        Long storeId;
        String memberNo;
        String name;
        String mobile;
        String gender;
        Integer age;
        String avatarUrl;
        String status;
        LocalDateTime joinedAt;
        LocalDateTime deletedAt;
        LocalDateTime createdAt;
        LocalDateTime updatedAt;
    }

    private static class WalletRecord {
        Long memberId;
        BigDecimal rechargeBalance = BigDecimal.ZERO.setScale(2);
        BigDecimal giftBalance = BigDecimal.ZERO.setScale(2);
        BigDecimal accumulatedRecharge = BigDecimal.ZERO.setScale(2);
        BigDecimal accumulatedGift = BigDecimal.ZERO.setScale(2);
        BigDecimal accumulatedConsumption = BigDecimal.ZERO.setScale(2);
        int rechargeCount;
        int consumptionCount;
        int version;

        WalletRecord(Long memberId) {
            this.memberId = memberId;
        }

        BigDecimal total() {
            return rechargeBalance.add(giftBalance).setScale(2);
        }

        WalletSnapshot snapshot() {
            return new WalletSnapshot(rechargeBalance, giftBalance);
        }
    }

    private static class TierRecord {
        int tierNo;
        BigDecimal rechargeAmount;
        BigDecimal giftAmount;
        LocalDateTime updatedAt = LocalDateTime.now();

        TierRecord(int tierNo, BigDecimal rechargeAmount, BigDecimal giftAmount) {
            this.tierNo = tierNo;
            this.rechargeAmount = rechargeAmount.setScale(2);
            this.giftAmount = giftAmount.setScale(2);
        }
    }

    private static class TransactionRecord {
        Long id;
        String serialNo;
        Long storeId;
        Long memberId;
        Long originalTransactionId;
        String type;
        String status;
        BigDecimal amount;
        BigDecimal giftAmount;
        BigDecimal beforeRechargeBalance;
        BigDecimal beforeGiftBalance;
        BigDecimal beforeTotalBalance;
        BigDecimal afterRechargeBalance;
        BigDecimal afterGiftBalance;
        BigDecimal afterTotalBalance;
        String paymentMethod;
        String orderNo;
        String itemName;
        String remark;
        String operatorType;
        Long operatorId;
        LocalDateTime reversedAt;
        LocalDateTime createdAt;
    }
}
