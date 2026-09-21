package com.gechuang.stationery.payment;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.ErrorCode;
import com.gechuang.stationery.mainflow.MainFlowMapper;
import com.gechuang.stationery.mainflow.MainFlowService;
import com.gechuang.stationery.payment.gateway.WechatPaymentGateway;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WechatPaymentOrderService {

    private static final int ORDER_EXPIRY_MINUTES = 15;
    private static final int QUERY_THROTTLE_SECONDS = 5;

    private final MainFlowMapper mapper;
    private final MainFlowService mainFlowService;
    private final StorePaymentCapabilityService capabilityService;
    private final WechatPaymentGateway gateway;
    private final WechatPayProperties properties;

    public WechatPaymentOrderService(MainFlowMapper mapper,
                                     MainFlowService mainFlowService,
                                     StorePaymentCapabilityService capabilityService,
                                     WechatPaymentGateway gateway,
                                     WechatPayProperties properties) {
        this.mapper = mapper;
        this.mainFlowService = mainFlowService;
        this.capabilityService = capabilityService;
        this.gateway = gateway;
        this.properties = properties;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createNativeOrder(Map<String, Object> store,
                                                 Long memberId,
                                                 BigDecimal requestedAmount,
                                                 String clientIdempotencyKey,
                                                 String payerClientIp) {
        Long storeId = longValue(store.get("id"));
        String idempotencyKey = orderIdempotencyKey(storeId, clientIdempotencyKey);
        BigDecimal amount = exactMoney(requestedAmount, true);
        Map<String, Object> existing = mapper.selectWechatPaymentOrderByIdempotencyKey(storeId, idempotencyKey);
        if (existing != null && !existing.isEmpty()) {
            verifyOrderRequest(existing, memberId, amount);
            return publicOrder(existing);
        }

        RealPaymentSettings settings = capabilityService.requireRealPaymentSettings(storeId);
        if (mapper.selectMemberDetail(memberId, storeId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "会员不存在");
        }
        BigDecimal giftAmount = mainFlowService.calculateRechargeGift(storeId, amount);
        String outTradeNo = outTradeNo();
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("outTradeNo", outTradeNo);
        order.put("storeId", storeId);
        order.put("memberId", memberId);
        order.put("rechargeAmount", amount);
        order.put("giftAmount", giftAmount);
        order.put("spMchid", settings.spMchid());
        order.put("subMchid", settings.subMchid());
        order.put("appid", settings.spAppId());
        order.put("idempotencyKey", idempotencyKey);
        order.put("status", "CREATED");
        order.put("expiresAt", LocalDateTime.now().plusMinutes(ORDER_EXPIRY_MINUTES));
        requireSingleRow(mapper.insertWechatPaymentOrder(order), "创建本地支付单失败");

        WechatPaymentGateway.NativeOrder nativeOrder = gateway.createNativeOrder(
                new WechatPaymentGateway.CreateNativeOrder(
                        outTradeNo,
                        settings.subMchid(),
                        "会员充值",
                        amount,
                        payerClientIp,
                        String.valueOf(storeId),
                        textOrDefault(store.get("shopName"), "文具店"),
                        null,
                        text(store.get("address"))));
        requireSingleRow(mapper.updateWechatPaymentOrderPrepay(
                outTradeNo, nativeOrder.codeUrl(), "WAITING_PAYMENT"), "保存微信预支付结果失败");
        order.put("codeUrl", nativeOrder.codeUrl());
        order.put("status", "WAITING_PAYMENT");
        return publicOrder(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> getOrder(Long storeId, String outTradeNo) {
        Map<String, Object> order = mapper.selectWechatPaymentOrderByStore(outTradeNo, storeId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "支付单不存在");
        }
        if (!"WAITING_PAYMENT".equals(text(order.get("status")))) {
            return publicOrder(order);
        }
        LocalDateTime now = LocalDateTime.now();
        if (mapper.claimWechatPaymentOrderQuery(outTradeNo, storeId, now,
                now.minusSeconds(QUERY_THROTTLE_SECONDS)) == 0) {
            return publicOrder(order);
        }
        WechatPaymentGateway.GatewayPayment payment = gateway.queryByOutTradeNo(
                outTradeNo, text(order.get("subMchid")));
        applyGatewayPayment(payment, false);
        return publicOrder(mapper.selectWechatPaymentOrderByStore(outTradeNo, storeId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handleNotification(WechatPaymentGateway.WechatNotification notification) {
        return applyGatewayPayment(gateway.parseNotification(notification), true);
    }

    @Transactional(rollbackFor = Exception.class)
    public int closeExpiredOrders() {
        int closed = 0;
        List<Map<String, Object>> orders = mapper.selectExpiredWechatPaymentOrders(LocalDateTime.now(), 50);
        for (Map<String, Object> order : orders) {
            Long id = longValue(order.get("id"));
            if (mapper.claimWechatPaymentOrderForClose(id) == 0) {
                continue;
            }
            String outTradeNo = text(order.get("outTradeNo"));
            try {
                gateway.closeOrder(outTradeNo, text(order.get("subMchid")));
            } catch (RuntimeException exception) {
                mapper.updateWechatPaymentOrderTerminal(
                        outTradeNo, "WAITING_PAYMENT", null, "CLOSE_RETRY", "等待重试关闭", null);
                continue;
            }
            requireSingleRow(mapper.updateWechatPaymentOrderTerminal(
                    outTradeNo, "CLOSED", null, "EXPIRED", "支付单已超时关闭", null),
                    "关闭本地支付单失败");
            closed++;
        }
        return closed;
    }

    private Map<String, Object> applyGatewayPayment(WechatPaymentGateway.GatewayPayment payment,
                                                    boolean fromNotification) {
        Map<String, Object> order = mapper.selectWechatPaymentOrderForUpdate(payment.outTradeNo());
        if (order == null) {
            if (fromNotification) {
                throw new InvalidWechatPaymentNotificationException("支付单不存在");
            }
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "支付单不存在");
        }
        verifyGatewayPayment(order, payment, fromNotification);
        String state = text(payment.tradeState());
        if ("SUCCESS".equals(state)) {
            String idempotencyKey = "wechat-native:" + payment.outTradeNo();
            if ("SUCCESS".equals(text(order.get("status")))) {
                Map<String, Object> existing = mapper.selectSuccessfulTransactionByIdempotencyKey(idempotencyKey);
                return existing == null ? order : existing;
            }
            Long storeId = longValue(order.get("storeId"));
            Long memberId = longValue(order.get("memberId"));
            if (mapper.selectMemberDetail(memberId, storeId) == null) {
                throw new IllegalStateException("支付单会员归属不一致");
            }
            Map<String, Object> transaction;
            try {
                transaction = mainFlowService.applyConfirmedWechatRecharge(
                        storeId,
                        memberId,
                        decimal(order.get("rechargeAmount")),
                        decimal(order.get("giftAmount")),
                        payment.outTradeNo());
            } catch (BusinessException exception) {
                throw new IllegalStateException("支付成功后的钱包入账失败", exception);
            }
            Long transactionId = longValue(transaction.get("id"));
            requireSingleRow(mapper.linkWechatPaymentOrderTransaction(payment.outTradeNo(), transactionId),
                    "关联充值流水失败");
            requireSingleRow(mapper.updateWechatPaymentOrderTerminal(
                    payment.outTradeNo(), "SUCCESS", payment.transactionId(), null, null,
                    paidAt(payment.successTime())), "确认支付成功状态失败");
            return transaction;
        }
        if (List.of("CLOSED", "REVOKED").contains(state)) {
            requireSingleRow(mapper.updateWechatPaymentOrderTerminal(
                    payment.outTradeNo(), "CLOSED", payment.transactionId(), state,
                    safeFailureMessage(payment.tradeStateDescription()), null), "保存支付关闭状态失败");
        } else if (List.of("PAYERROR", "REFUND").contains(state)) {
            requireSingleRow(mapper.updateWechatPaymentOrderTerminal(
                    payment.outTradeNo(), "FAILED", payment.transactionId(), state,
                    safeFailureMessage(payment.tradeStateDescription()), null), "保存支付失败状态失败");
        }
        return order;
    }

    private void verifyGatewayPayment(Map<String, Object> order, WechatPaymentGateway.GatewayPayment payment,
                                      boolean fromNotification) {
        boolean matches = Objects.equals(text(order.get("outTradeNo")), payment.outTradeNo())
                && Objects.equals(text(order.get("spMchid")), payment.spMchid())
                && Objects.equals(text(order.get("subMchid")), payment.subMchid())
                && Objects.equals(text(order.get("appid")), payment.spAppId())
                && payment.amount() != null
                && decimal(order.get("rechargeAmount")).compareTo(payment.amount()) == 0
                && Objects.equals(properties.getWechat().getSpMchid(), payment.spMchid())
                && Objects.equals(properties.getWechat().getSpAppId(), payment.spAppId());
        if (!matches) {
            if (fromNotification) {
                throw new InvalidWechatPaymentNotificationException("微信支付结果不匹配");
            }
            throw new BusinessException(ErrorCode.BIZ_409, "微信支付结果不匹配");
        }
    }

    private void verifyOrderRequest(Map<String, Object> order, Long memberId, BigDecimal amount) {
        if (!Objects.equals(longValue(order.get("memberId")), memberId)
                || decimal(order.get("rechargeAmount")).compareTo(amount) != 0) {
            throw new BusinessException(ErrorCode.BIZ_409, "幂等键对应的支付请求不一致");
        }
    }

    private String orderIdempotencyKey(Long storeId, String clientIdempotencyKey) {
        if (!StringUtils.hasText(clientIdempotencyKey) || clientIdempotencyKey.length() > 96) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "幂等键不能为空且长度不能超过 96");
        }
        return "mini-native:" + storeId + ":" + clientIdempotencyKey.trim();
    }

    private BigDecimal exactMoney(BigDecimal amount, boolean positive) {
        if (amount == null || (positive && amount.signum() <= 0)) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "支付金额必须大于 0");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "支付金额必须精确到分");
        }
    }

    private String outTradeNo() {
        return "MP" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private LocalDateTime paidAt(String successTime) {
        if (!StringUtils.hasText(successTime)) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(successTime).toLocalDateTime();
        } catch (DateTimeParseException exception) {
            return LocalDateTime.now();
        }
    }

    private String safeFailureMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        return message.length() <= 255 ? message : message.substring(0, 255);
    }

    private Map<String, Object> publicOrder(Map<String, Object> order) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("outTradeNo", "memberId", "rechargeAmount", "giftAmount", "codeUrl",
                "status", "failureCode", "expiresAt", "paidAt", "createdAt", "updatedAt")) {
            if (order.containsKey(key)) {
                result.put(key, order.get(key));
            }
        }
        return result;
    }

    private void requireSingleRow(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new IllegalStateException(message);
        }
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.UNNECESSARY);
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String textOrDefault(Object value, String fallback) {
        String text = text(value);
        return StringUtils.hasText(text) ? text : fallback;
    }
}
