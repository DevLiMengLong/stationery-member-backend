package com.gechuang.stationery.mainflow;

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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MainFlowService {

    public static final String MERCHANT_TOKEN = "MERCHANT_TOKEN";
    public static final String ADMIN_TOKEN = "ADMIN_TOKEN";
    public static final int TOKEN_MAX_AGE_SECONDS = 12 * 60 * 60;

    private static final DateTimeFormatter MEMBER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter SERIAL_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final MainFlowMapper mapper;

    public MainFlowService(MainFlowMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthResult loginMerchant(String account, String password) {
        Map<String, Object> store = mapper.selectStoreByAccount(text(account));
        if (store == null || !Objects.equals(store.get("passwordHash"), hash(text(password)))) {
            throw new BusinessException(ErrorCode.AUTH_401, "商家账号或密码错误");
        }
        requireActive(store);
        Long storeId = longValue(store.get("id"));
        mapper.updateStoreLastLogin(storeId);
        String token = createSession("MERCHANT", storeId);
        return new AuthResult(token, publicStore(mapper.selectStoreById(storeId)));
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthResult loginAdmin(String username, String password) {
        Map<String, Object> admin = mapper.selectAdminByUsername(text(username));
        if (admin == null || !Objects.equals(admin.get("passwordHash"), hash(text(password)))) {
            throw new BusinessException(ErrorCode.AUTH_401, "管理员账号或密码错误");
        }
        requireActive(admin);
        Long adminId = longValue(admin.get("id"));
        mapper.updateAdminLastLogin(adminId);
        String token = createSession("ADMIN", adminId);
        return new AuthResult(token, publicAdmin(mapper.selectAdminById(adminId)));
    }

    public Map<String, Object> requireMerchant(HttpServletRequest request) {
        String token = tokenFromCookie(request, MERCHANT_TOKEN);
        Map<String, Object> store = mapper.selectStoreByToken(token, LocalDateTime.now());
        if (store == null) {
            throw new BusinessException(ErrorCode.AUTH_401, ErrorCode.AUTH_401.getDefaultMessage());
        }
        requireActive(store);
        return publicStore(store);
    }

    public Map<String, Object> requireAdmin(HttpServletRequest request) {
        String token = tokenFromCookie(request, ADMIN_TOKEN);
        Map<String, Object> admin = mapper.selectAdminByToken(token, LocalDateTime.now());
        if (admin == null) {
            throw new BusinessException(ErrorCode.AUTH_401, ErrorCode.AUTH_401.getDefaultMessage());
        }
        requireActive(admin);
        return publicAdmin(admin);
    }

    public void logout(HttpServletRequest request, String cookieName) {
        mapper.deleteSession(tokenFromCookie(request, cookieName));
    }

    public Map<String, Object> merchantDashboard(Map<String, Object> store) {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L).atStartOfDay();
        Map<String, Object> row = mapper.selectMerchantDashboard(longValue(store.get("id")), todayStart,
                tomorrowStart, weekStart);
        Map<String, Object> result = publicStore(row);
        result.put("updatedAt", row.get("updatedAt"));
        result.put("memberCount", row.get("memberCount"));
        result.put("totalRechargeAmount", money(row.get("totalRechargeAmount")));
        result.put("pendingConsumptionAmount", money(row.get("pendingConsumptionAmount")));
        result.put("totalGiftAmount", money(row.get("totalGiftAmount")));
        result.put("today", Map.of(
                "newMembers", row.get("todayNewMembers"),
                "consumptionAmount", money(row.get("todayConsumptionAmount")),
                "rechargeAmount", money(row.get("todayRechargeAmount"))
        ));
        result.put("week", Map.of(
                "newMembers", row.get("weekNewMembers"),
                "consumptionAmount", money(row.get("weekConsumptionAmount")),
                "rechargeAmount", money(row.get("weekRechargeAmount"))
        ));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateMerchantProfile(Map<String, Object> store, Map<String, Object> body) {
        String shopName = text(body.get("shopName"));
        if (!StringUtils.hasText(shopName) || shopName.length() > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "店铺名称长度需为 1-50");
        }
        mapper.updateMerchantProfile(longValue(store.get("id")), shopName, textOrDefault(body.get("avatarUrl"),
                text(store.get("avatarUrl"))));
        return publicStore(mapper.selectStoreById(longValue(store.get("id"))));
    }

    public void updateMerchantPassword(Map<String, Object> store, Map<String, Object> body) {
        String newPassword = text(body.get("newPassword"));
        validatePassword(newPassword);
        mapper.updateStorePassword(longValue(store.get("id")), hash(newPassword));
    }

    public List<Map<String, Object>> getRechargeTiers(Long storeId) {
        return mapper.selectRechargeTiers(storeId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateRechargeTier(Long storeId, int tierNo, Map<String, Object> body) {
        BigDecimal rechargeAmount = decimal(body.get("rechargeAmount"));
        BigDecimal giftAmount = decimal(body.get("giftAmount"));
        if (rechargeAmount.compareTo(BigDecimal.ZERO) <= 0 || giftAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "充值金额必须大于 0，赠送金额不能小于 0");
        }
        if (mapper.selectRechargeTier(storeId, tierNo) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "档位不存在");
        }
        mapper.updateRechargeTier(storeId, tierNo, rechargeAmount, giftAmount);
        if (!isIncreasing(mapper.selectRechargeTiers(storeId))) {
            throw new BusinessException(ErrorCode.BIZ_409, "充值金额必须按档位递增");
        }
        return mapper.selectRechargeTier(storeId, tierNo);
    }

    public List<Map<String, Object>> campaigns(Long storeId) {
        List<Map<String, Object>> tiers = mapper.selectRechargeTiers(storeId);
        if (tiers.isEmpty()) {
            return List.of();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", storeId);
        row.put("storeId", storeId);
        row.put("campaignName", "当前充值梯度");
        row.put("status", "SAVED");
        row.put("summary", String.join("，", tiers.stream()
                .map(tier -> "充" + money(tier.get("rechargeAmount")) + "送" + money(tier.get("giftAmount")))
                .toList()));
        return List.of(row);
    }

    public PageResult<Map<String, Object>> merchantMembers(Long storeId, String keyword, int pageNo, int pageSize) {
        Page page = page(pageNo, pageSize);
        long total = mapper.countMerchantMembers(storeId, keyword);
        return new PageResult<>(mapper.selectMerchantMembers(storeId, keyword, page.offset(), page.limit()),
                total, page.pageNo(), page.pageSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createMember(Long storeId, Map<String, Object> body) {
        String mobile = text(body.get("mobile"));
        if (!validMobile(mobile)) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "手机号格式不正确");
        }
        if (mapper.selectMemberByMobile(storeId, mobile) != null) {
            throw new BusinessException(ErrorCode.BIZ_409, "手机号已存在");
        }
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("storeId", storeId);
        member.put("memberNo", "M" + LocalDateTime.now().format(MEMBER_NO_TIME)
                + mobile.substring(mobile.length() - 4));
        member.put("mobile", mobile);
        member.put("name", requiredText(body.get("name"), "姓名不能为空"));
        member.put("gender", textOrDefault(body.get("gender"), "MALE"));
        member.put("age", intValue(body.get("age"), 0));
        member.put("avatarUrl", text(body.get("avatarUrl")));
        mapper.insertMember(member);
        mapper.insertWallet(longValue(member.get("id")));
        return mapper.selectMemberDetail(longValue(member.get("id")), storeId);
    }

    public Map<String, Object> memberDetail(Long storeId, Long id) {
        Map<String, Object> member = mapper.selectMemberDetail(id, storeId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "会员不存在");
        }
        return member;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateMember(Long storeId, Long id, Map<String, Object> body) {
        if (mapper.selectMemberDetail(id, storeId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "会员不存在");
        }
        String mobile = text(body.get("mobile"));
        if (StringUtils.hasText(mobile)) {
            Map<String, Object> duplicate = mapper.selectMemberByMobile(storeId, mobile);
            if (duplicate != null && !Objects.equals(longValue(duplicate.get("id")), id)) {
                throw new BusinessException(ErrorCode.BIZ_409, "手机号已存在");
            }
        }
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("id", id);
        member.put("storeId", storeId);
        member.put("mobile", mobile);
        member.put("name", text(body.get("name")));
        member.put("gender", text(body.get("gender")));
        member.put("age", body.get("age") == null ? null : intValue(body.get("age"), 0));
        member.put("avatarUrl", text(body.get("avatarUrl")));
        member.put("status", text(body.get("status")));
        mapper.updateMember(member);
        return memberDetail(storeId, id);
    }

    public void softDeleteMember(Long storeId, Long id) {
        int rows = mapper.softDeleteMember(id, storeId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "会员不存在");
        }
    }

    public Map<String, Object> lookupMember(Long storeId, String keyword) {
        String value = text(keyword);
        if (!StringUtils.hasText(value)) {
            return Map.of("matchType", "NONE");
        }
        if (validMobile(value)) {
            Map<String, Object> member = mapper.selectMemberByMobile(storeId, value);
            return member == null ? Map.of("matchType", "NONE") : Map.of("matchType", "MOBILE", "member", member);
        }
        if (value.length() == 4 && value.chars().allMatch(Character::isDigit)) {
            List<Map<String, Object>> candidates = mapper.selectMembersByMobileSuffix(storeId, value);
            if (candidates.isEmpty()) {
                return Map.of("matchType", "NONE");
            }
            if (candidates.size() == 1) {
                return Map.of("matchType", "SUFFIX_SINGLE", "member", candidates.get(0));
            }
            return Map.of("matchType", "SUFFIX_MULTIPLE", "candidates", candidates);
        }
        return Map.of("matchType", "NONE");
    }

    public PageResult<Map<String, Object>> memberTransactions(Long storeId, Long memberId, String type,
                                                              int pageNo, int pageSize) {
        if (mapper.selectMemberDetail(memberId, storeId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "会员不存在");
        }
        Page page = page(pageNo, pageSize);
        long total = mapper.countTransactions(memberId, storeId, type);
        return new PageResult<>(mapper.selectTransactions(memberId, storeId, type, page.offset(), page.limit()),
                total, page.pageNo(), page.pageSize());
    }

    public List<Map<String, Object>> recentTransactions(Long storeId, String type, int limit) {
        return mapper.selectRecentTransactions(storeId, type, Math.max(1, limit));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> recharge(Map<String, Object> store, Map<String, Object> body) {
        Long storeId = longValue(store.get("id"));
        Long memberId = longValue(body.get("memberId"));
        mapper.selectMemberDetailForUpdate(memberId, storeId);
        Map<String, Object> wallet = mapper.selectWalletForUpdate(memberId);
        BigDecimal amount = positiveAmount(body.get("amount"));
        BigDecimal gift = RechargeGiftCalculator.calculate(amount, tierRules(storeId));
        WalletSnapshot before = snapshot(wallet);
        WalletSnapshot after = new WalletSnapshot(
                before.rechargeBalance().add(amount).setScale(2, RoundingMode.HALF_UP),
                before.giftBalance().add(gift).setScale(2, RoundingMode.HALF_UP)
        );
        applyWallet(wallet, after,
                decimal(wallet.get("accumulatedRecharge")).add(amount),
                decimal(wallet.get("accumulatedGift")).add(gift),
                decimal(wallet.get("accumulatedConsumption")),
                intValue(wallet.get("rechargeCount"), 0) + 1,
                intValue(wallet.get("consumptionCount"), 0));
        mapper.updateWallet(wallet);
        return insertTransaction(storeId, memberId, null, text(body.get("idempotencyKey")), "RECHARGE",
                amount, gift, before, after, textOrDefault(body.get("paymentMethod"), "CASH"),
                textOrDefault(body.get("itemName"), "会员充值"), text(body.get("remark")), "MERCHANT", storeId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> consume(Map<String, Object> store, Map<String, Object> body) {
        Long storeId = longValue(store.get("id"));
        Long memberId = longValue(body.get("memberId"));
        mapper.selectMemberDetailForUpdate(memberId, storeId);
        Map<String, Object> wallet = mapper.selectWalletForUpdate(memberId);
        BigDecimal amount = positiveAmount(body.get("amount"));
        WalletSnapshot before = snapshot(wallet);
        WalletSnapshot after;
        try {
            after = WalletCalculator.consume(before, amount);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BIZ_409, "余额不足");
        }
        applyWallet(wallet, after,
                decimal(wallet.get("accumulatedRecharge")),
                decimal(wallet.get("accumulatedGift")),
                decimal(wallet.get("accumulatedConsumption")).add(amount),
                intValue(wallet.get("rechargeCount"), 0),
                intValue(wallet.get("consumptionCount"), 0) + 1);
        mapper.updateWallet(wallet);
        return insertTransaction(storeId, memberId, null, text(body.get("idempotencyKey")), "CONSUMPTION",
                amount, BigDecimal.ZERO.setScale(2), before, after, textOrDefault(body.get("paymentMethod"), "CASH"),
                textOrDefault(body.get("itemName"), "消费扣款"), text(body.get("remark")), "MERCHANT", storeId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reverse(Long storeId, Long transactionId, String operatorType, Long operatorId) {
        Map<String, Object> source = mapper.selectTransactionForUpdate(transactionId, storeId);
        if (source == null || !"RECHARGE".equals(text(value(source, "type"))) || !"SUCCESS".equals(text(value(source, "status")))) {
            throw new BusinessException(ErrorCode.BIZ_409, "仅成功充值流水可撤销");
        }
        Long memberId = longValue(value(source, "member_id", "memberId"));
        Map<String, Object> wallet = mapper.selectWalletForUpdate(memberId);
        WalletSnapshot before = snapshot(wallet);
        BigDecimal amount = decimal(value(source, "amount"));
        BigDecimal gift = decimal(value(source, "gift_amount", "giftAmount"));
        WalletSnapshot after;
        try {
            after = WalletCalculator.deduct(before, amount.add(gift));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BIZ_409, "余额不足，无法撤销充值");
        }
        applyWallet(wallet, after,
                decimal(wallet.get("accumulatedRecharge")).subtract(amount).max(BigDecimal.ZERO),
                decimal(wallet.get("accumulatedGift")).subtract(gift).max(BigDecimal.ZERO),
                decimal(wallet.get("accumulatedConsumption")),
                intValue(wallet.get("rechargeCount"), 0),
                intValue(wallet.get("consumptionCount"), 0));
        mapper.updateWallet(wallet);
        mapper.markTransactionReversed(transactionId);
        return insertTransaction(longValue(value(source, "store_id", "storeId")), memberId, transactionId, null,
                "RECHARGE_REVERSAL", amount, gift, before, after, text(value(source, "payment_method", "paymentMethod")),
                "充值撤销", null, operatorType, operatorId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> refund(Long storeId, Long transactionId, BigDecimal refundAmount,
                                      String operatorType, Long operatorId) {
        Map<String, Object> source = mapper.selectTransactionForUpdate(transactionId, storeId);
        if (source == null || !"CONSUMPTION".equals(text(value(source, "type"))) || !"SUCCESS".equals(text(value(source, "status")))) {
            throw new BusinessException(ErrorCode.BIZ_409, "仅成功扣款流水可退款");
        }
        BigDecimal amount = positiveAmount(refundAmount);
        Long memberId = longValue(value(source, "member_id", "memberId"));
        Map<String, Object> wallet = mapper.selectWalletForUpdate(memberId);
        WalletSnapshot before = snapshot(wallet);
        WalletSnapshot after = new WalletSnapshot(before.rechargeBalance().add(amount).setScale(2), before.giftBalance());
        applyWallet(wallet, after,
                decimal(wallet.get("accumulatedRecharge")),
                decimal(wallet.get("accumulatedGift")),
                decimal(wallet.get("accumulatedConsumption")).subtract(amount).max(BigDecimal.ZERO),
                intValue(wallet.get("rechargeCount"), 0),
                intValue(wallet.get("consumptionCount"), 0));
        mapper.updateWallet(wallet);
        return insertTransaction(longValue(value(source, "store_id", "storeId")), memberId, transactionId, null,
                "CONSUMPTION_REFUND", amount, BigDecimal.ZERO.setScale(2), before, after,
                text(value(source, "payment_method", "paymentMethod")), "消费退款", null, operatorType, operatorId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> correction(Long memberId, String correctionType, BigDecimal amount, String reason,
                                          Long adminId) {
        mapper.selectMemberDetailForUpdate(memberId, null);
        Map<String, Object> wallet = mapper.selectWalletForUpdate(memberId);
        WalletSnapshot before = snapshot(wallet);
        BigDecimal value = positiveAmount(amount);
        BigDecimal recharge = before.rechargeBalance();
        BigDecimal gift = before.giftBalance();
        switch (text(correctionType)) {
            case "ADD_RECHARGE" -> recharge = recharge.add(value).setScale(2);
            case "ADD_GIFT" -> gift = gift.add(value).setScale(2);
            case "DEDUCT_RECHARGE" -> recharge = recharge.subtract(value).max(BigDecimal.ZERO).setScale(2);
            case "DEDUCT_GIFT" -> gift = gift.subtract(value).max(BigDecimal.ZERO).setScale(2);
            default -> throw new BusinessException(ErrorCode.VALIDATION_422, "冲正类型不正确");
        }
        WalletSnapshot after = new WalletSnapshot(recharge, gift);
        applyWallet(wallet, after, decimal(wallet.get("accumulatedRecharge")), decimal(wallet.get("accumulatedGift")),
                decimal(wallet.get("accumulatedConsumption")), intValue(wallet.get("rechargeCount"), 0),
                intValue(wallet.get("consumptionCount"), 0));
        mapper.updateWallet(wallet);
        Map<String, Object> member = mapper.selectMemberDetail(memberId, null);
        return insertTransaction(longValue(member.get("storeId")), memberId, null, null, "MANUAL_CORRECTION",
                value, BigDecimal.ZERO.setScale(2), before, after, "OTHER", "人工冲正", reason, "ADMIN", adminId);
    }

    public Map<String, Object> adminOverview() {
        Map<String, Object> overview = new LinkedHashMap<>(mapper.selectAdminOverview());
        overview.put("stores", mapper.selectOverviewStores());
        overview.put("totalRechargeAmount", money(overview.get("totalRechargeAmount")));
        overview.put("pendingConsumptionAmount", money(overview.get("pendingConsumptionAmount")));
        return overview;
    }

    public PageResult<Map<String, Object>> stores(String keyword, String status, int pageNo, int pageSize) {
        Page page = page(pageNo, pageSize);
        long total = mapper.countStores(keyword, status);
        return new PageResult<>(mapper.selectStores(keyword, status, page.offset(), page.limit()),
                total, page.pageNo(), page.pageSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createStore(Map<String, Object> body) {
        String account = requiredText(body.get("account"), "商家账号不能为空");
        if (mapper.selectStoreByAccount(account) != null) {
            throw new BusinessException(ErrorCode.BIZ_409, "商家账号已存在");
        }
        Map<String, Object> store = new LinkedHashMap<>();
        store.put("account", account);
        store.put("passwordHash", hash(textOrDefault(body.get("initialPassword"), "123456")));
        store.put("shopName", textOrDefault(body.get("shopName"), account));
        store.put("contactMobile", textOrDefault(body.get("contactMobile"), "13800000000"));
        store.put("avatarUrl", text(body.get("avatarUrl")));
        store.put("address", text(body.get("address")));
        store.put("status", textOrDefault(body.get("status"), "ACTIVE"));
        mapper.insertStore(store);
        mapper.insertDefaultRechargeTiers(longValue(store.get("id")));
        return publicStore(mapper.selectStoreById(longValue(store.get("id"))));
    }

    public Map<String, Object> updateStore(Long id, Map<String, Object> body) {
        Map<String, Object> existing = mapper.selectStoreById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "门店不存在");
        }
        Map<String, Object> store = new LinkedHashMap<>();
        store.put("id", id);
        store.put("shopName", textOrDefault(body.get("shopName"), text(existing.get("shopName"))));
        store.put("contactMobile", textOrDefault(body.get("contactMobile"), text(existing.get("contactMobile"))));
        store.put("avatarUrl", textOrDefault(body.get("avatarUrl"), text(existing.get("avatarUrl"))));
        store.put("address", textOrDefault(body.get("address"), text(existing.get("address"))));
        store.put("status", textOrDefault(body.get("status"), text(existing.get("status"))));
        mapper.updateStore(store);
        return publicStore(mapper.selectStoreById(id));
    }

    public Map<String, Object> updateStoreStatus(Long id, String status) {
        if (!List.of("ACTIVE", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "门店状态不正确");
        }
        if (mapper.updateStoreStatus(id, status) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "门店不存在");
        }
        return publicStore(mapper.selectStoreById(id));
    }

    public void resetStorePassword(Long id, String newPassword) {
        validatePassword(newPassword);
        if (mapper.updateStorePassword(id, hash(newPassword)) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "门店不存在");
        }
    }

    public PageResult<Map<String, Object>> adminMembers(String mobile, String name, Long storeId,
                                                        BigDecimal rechargeMin, BigDecimal rechargeMax,
                                                        BigDecimal consumptionMin, BigDecimal consumptionMax,
                                                        int pageNo, int pageSize) {
        Page page = page(pageNo, pageSize);
        long total = mapper.countAdminMembers(mobile, name, storeId, rechargeMin, rechargeMax,
                consumptionMin, consumptionMax);
        return new PageResult<>(mapper.selectAdminMembers(mobile, name, storeId, rechargeMin, rechargeMax,
                consumptionMin, consumptionMax, page.offset(), page.limit()), total, page.pageNo(), page.pageSize());
    }

    public List<Map<String, Object>> adminMembersForExport(String mobile, String name, Long storeId,
                                                           BigDecimal rechargeMin, BigDecimal rechargeMax,
                                                           BigDecimal consumptionMin, BigDecimal consumptionMax) {
        return mapper.selectAdminMembersForExport(mobile, name, storeId, rechargeMin, rechargeMax,
                consumptionMin, consumptionMax, 5001);
    }

    public List<Map<String, Object>> activityRows() {
        return mapper.selectActivityRows();
    }

    public record AuthResult(String token, Map<String, Object> user) {
    }

    private String createSession(String principalType, Long principalId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        mapper.insertSession(token, principalType, principalId,
                LocalDateTime.now().plusSeconds(TOKEN_MAX_AGE_SECONDS));
        return token;
    }

    private Map<String, Object> insertTransaction(Long storeId, Long memberId, Long originalTransactionId,
                                                  String idempotencyKey, String type, BigDecimal amount,
                                                  BigDecimal giftAmount, WalletSnapshot before, WalletSnapshot after,
                                                  String paymentMethod, String itemName, String remark,
                                                  String operatorType, Long operatorId) {
        Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("serialNo", serialNo(type));
        tx.put("storeId", storeId);
        tx.put("memberId", memberId);
        tx.put("originalTransactionId", originalTransactionId);
        tx.put("idempotencyKey", idempotencyKey);
        tx.put("type", type);
        tx.put("status", "SUCCESS");
        tx.put("amount", amount);
        tx.put("giftAmount", giftAmount);
        tx.put("beforeRechargeBalance", before.rechargeBalance());
        tx.put("beforeGiftBalance", before.giftBalance());
        tx.put("beforeTotalBalance", before.totalBalance());
        tx.put("afterRechargeBalance", after.rechargeBalance());
        tx.put("afterGiftBalance", after.giftBalance());
        tx.put("afterTotalBalance", after.totalBalance());
        tx.put("paymentMethod", paymentMethod);
        tx.put("itemName", itemName);
        tx.put("remark", remark);
        tx.put("operatorType", operatorType);
        tx.put("operatorId", operatorId);
        mapper.insertTransaction(tx);
        Map<String, Object> member = mapper.selectMemberDetail(memberId, null);
        tx.put("storeName", member.get("storeName"));
        tx.put("memberName", member.get("name"));
        tx.put("mobile", member.get("mobile"));
        tx.put("orderNo", tx.get("serialNo"));
        tx.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        tx.put("amount", money(amount));
        tx.put("giftAmount", money(giftAmount));
        tx.put("beforeTotalBalance", money(before.totalBalance()));
        tx.put("afterTotalBalance", money(after.totalBalance()));
        return tx;
    }

    private String serialNo(String type) {
        String prefix = switch (type) {
            case "RECHARGE" -> "RC";
            case "CONSUMPTION" -> "CP";
            case "CONSUMPTION_REFUND" -> "RF";
            case "RECHARGE_REVERSAL" -> "RV";
            case "MANUAL_CORRECTION" -> "CR";
            default -> "TX";
        };
        return prefix + LocalDateTime.now().format(SERIAL_TIME) + UUID.randomUUID().toString().substring(0, 6);
    }

    private List<RechargeTierRule> tierRules(Long storeId) {
        return mapper.selectRechargeTiers(storeId).stream()
                .map(row -> new RechargeTierRule(intValue(row.get("tierNo"), 0),
                        decimal(row.get("rechargeAmount")), decimal(row.get("giftAmount"))))
                .toList();
    }

    private boolean isIncreasing(List<Map<String, Object>> tiers) {
        BigDecimal previous = BigDecimal.ZERO;
        for (Map<String, Object> tier : tiers) {
            BigDecimal current = decimal(tier.get("rechargeAmount"));
            if (current.compareTo(previous) <= 0) {
                return false;
            }
            previous = current;
        }
        return true;
    }

    private WalletSnapshot snapshot(Map<String, Object> wallet) {
        if (wallet == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "钱包不存在");
        }
        return new WalletSnapshot(decimal(wallet.get("rechargeBalance")), decimal(wallet.get("giftBalance")));
    }

    private void applyWallet(Map<String, Object> wallet, WalletSnapshot after,
                             BigDecimal accumulatedRecharge, BigDecimal accumulatedGift,
                             BigDecimal accumulatedConsumption, int rechargeCount, int consumptionCount) {
        wallet.put("rechargeBalance", after.rechargeBalance().setScale(2));
        wallet.put("giftBalance", after.giftBalance().setScale(2));
        wallet.put("accumulatedRecharge", accumulatedRecharge.setScale(2, RoundingMode.HALF_UP));
        wallet.put("accumulatedGift", accumulatedGift.setScale(2, RoundingMode.HALF_UP));
        wallet.put("accumulatedConsumption", accumulatedConsumption.setScale(2, RoundingMode.HALF_UP));
        wallet.put("rechargeCount", rechargeCount);
        wallet.put("consumptionCount", consumptionCount);
    }

    private Map<String, Object> publicStore(Map<String, Object> source) {
        if (source == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.remove("passwordHash");
        return result;
    }

    private Map<String, Object> publicAdmin(Map<String, Object> source) {
        if (source == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.remove("passwordHash");
        return result;
    }

    private void requireActive(Map<String, Object> principal) {
        if (!"ACTIVE".equals(text(principal.get("status")))) {
            throw new BusinessException(ErrorCode.AUTH_423, ErrorCode.AUTH_423.getDefaultMessage());
        }
    }

    private String tokenFromCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return "";
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return "";
    }

    private Page page(int pageNo, int pageSize) {
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, pageSize);
        return new Page(safePageNo, safePageSize, (safePageNo - 1) * safePageSize, safePageSize);
    }

    private String requiredText(Object value, String message) {
        String result = text(value);
        if (!StringUtils.hasText(result)) {
            throw new BusinessException(ErrorCode.VALIDATION_422, message);
        }
        return result;
    }

    private void validatePassword(String password) {
        if (!StringUtils.hasText(password) || password.length() < 6 || password.length() > 32) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "密码长度需为 6-32");
        }
    }

    private boolean validMobile(String mobile) {
        return mobile != null && mobile.matches("^1[3-9]\\d{9}$");
    }

    private BigDecimal positiveAmount(Object value) {
        BigDecimal amount = decimal(value);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "金额必须大于 0");
        }
        return amount;
    }

    private BigDecimal decimal(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return BigDecimal.ZERO.setScale(2);
        }
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }

    private String money(Object value) {
        return decimal(value).toPlainString();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private int intValue(Object value, int fallback) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Object value(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String textOrDefault(Object value, String fallback) {
        String result = text(value);
        return StringUtils.hasText(result) ? result : fallback;
    }

    private BigDecimal positiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "金额必须大于 0");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(textOrDefault(value, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Page(int pageNo, int pageSize, int offset, int limit) {
    }
}
