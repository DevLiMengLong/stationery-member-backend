package com.gechuang.stationery.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.mainflow.MainFlowMapper;
import com.gechuang.stationery.mainflow.MainFlowService;
import com.gechuang.stationery.notification.MemberNotificationService;
import com.gechuang.stationery.payment.gateway.WechatPaymentGateway;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WechatPaymentOrderServiceTest {

    private final MainFlowMapper mapper = mock(MainFlowMapper.class);
    private final MainFlowService mainFlowService = mock(MainFlowService.class);
    private final StorePaymentCapabilityService capabilityService = mock(StorePaymentCapabilityService.class);
    private final WechatPaymentGateway gateway = mock(WechatPaymentGateway.class);
    private final WechatPayProperties properties = paymentProperties();
    private WechatPaymentOrderService service;

    @BeforeEach
    void setUp() {
        service = new WechatPaymentOrderService(mapper, mainFlowService, capabilityService, gateway, properties);
        when(mapper.insertWechatPaymentOrder(anyMap())).thenReturn(1);
        when(mapper.updateWechatPaymentOrderPrepay(any(), any(), any())).thenReturn(1);
        when(mapper.linkWechatPaymentOrderTransaction(any(), any())).thenReturn(1);
        when(mapper.updateWechatPaymentOrderTerminal(
                any(), any(), nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(LocalDateTime.class)))
                .thenReturn(1);
    }

    @Test
    void bookkeepingStoreCannotCreateRealOrder() {
        when(capabilityService.requireRealPaymentSettings(1L))
                .thenThrow(new BusinessException(com.gechuang.stationery.common.ErrorCode.BIZ_409,
                        "当前门店仅支持记账模式"));

        assertThatThrownBy(() -> service.createNativeOrder(
                store(), 99L, new BigDecimal("100.00"), "request-1", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("记账模式");
        verify(gateway, never()).createNativeOrder(any());
    }

    @Test
    void enabledStoreReceivesCodeUrlAndSnapshotsGift() {
        enableRealPayment();
        when(mapper.selectMemberDetail(99L, 1L)).thenReturn(Map.of("id", 99L));
        when(mainFlowService.calculateRechargeGift(1L, new BigDecimal("100.00")))
                .thenReturn(new BigDecimal("10.00"));
        when(gateway.createNativeOrder(any()))
                .thenReturn(new WechatPaymentGateway.NativeOrder("weixin://wxpay/example"));

        Map<String, Object> result = service.createNativeOrder(
                store(), 99L, new BigDecimal("100.00"), "request-1", "127.0.0.1");

        assertThat(result.get("codeUrl")).isEqualTo("weixin://wxpay/example");
        assertThat(result.get("status")).isEqualTo("WAITING_PAYMENT");
        ArgumentCaptor<Map<String, Object>> order = mapCaptor();
        verify(mapper).insertWechatPaymentOrder(order.capture());
        assertThat(order.getValue().get("giftAmount")).isEqualTo(new BigDecimal("10.00"));
        assertThat(order.getValue().get("idempotencyKey")).isEqualTo("mini-native:1:request-1");
        verify(mapper).updateWechatPaymentOrderPrepay(
                String.valueOf(result.get("outTradeNo")), "weixin://wxpay/example", "WAITING_PAYMENT");
    }

    @Test
    void queryRejectsOrderOwnedByAnotherStoreBeforeCallingWechat() {
        when(mapper.selectWechatPaymentOrderByStore("ORDER-1", 1L)).thenReturn(null);

        assertThatThrownBy(() -> service.getOrder(1L, "ORDER-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("支付单不存在");
        verify(gateway, never()).queryByOutTradeNo(any(), any());
    }

    @Test
    void callbackRejectsAmountOrSubMerchantMismatchBeforeWalletMutation() {
        WechatPaymentGateway.WechatNotification notification = notification();
        when(gateway.parseNotification(notification)).thenReturn(payment("SUCCESS", "99.00", "WRONG-MCHID"));
        when(mapper.selectWechatPaymentOrderForUpdate("ORDER-1")).thenReturn(waitingOrder());

        assertThatThrownBy(() -> service.handleNotification(notification))
                .isInstanceOf(InvalidWechatPaymentNotificationException.class)
                .hasMessageContaining("支付结果不匹配");
        verify(mainFlowService, never()).applyConfirmedWechatRecharge(any(), any(), any(), any(), any());
    }

    @Test
    void duplicateSuccessCallbackReturnsExistingTransactionWithoutCreditingTwice() {
        Map<String, Object> completed = waitingOrder();
        completed.put("status", "SUCCESS");
        completed.put("walletTransactionId", 77L);
        when(gateway.parseNotification(notification())).thenReturn(payment("SUCCESS", "100.00", "1900000002"));
        when(mapper.selectWechatPaymentOrderForUpdate("ORDER-1")).thenReturn(completed);
        when(mapper.selectSuccessfulTransactionByIdempotencyKey("wechat-native:ORDER-1"))
                .thenReturn(Map.of("id", 77L));

        Map<String, Object> result = service.handleNotification(notification());

        assertThat(result.get("id")).isEqualTo(77L);
        verify(mainFlowService, never()).applyConfirmedWechatRecharge(any(), any(), any(), any(), any());
    }

    @Test
    void queryConfirmedSuccessCreditsSnapshottedGiftAndLinksTransaction() {
        Map<String, Object> order = waitingOrder();
        when(mapper.selectWechatPaymentOrderByStore("ORDER-1", 1L)).thenReturn(order);
        when(mapper.claimWechatPaymentOrderQuery(eq("ORDER-1"), eq(1L), any(), any())).thenReturn(1);
        when(gateway.queryByOutTradeNo("ORDER-1", "1900000002"))
                .thenReturn(payment("SUCCESS", "100.00", "1900000002"));
        when(mapper.selectWechatPaymentOrderForUpdate("ORDER-1")).thenReturn(order);
        when(mapper.selectMemberDetail(99L, 1L)).thenReturn(Map.of("id", 99L));
        when(mainFlowService.applyConfirmedWechatRecharge(
                1L, 99L, new BigDecimal("100.00"), new BigDecimal("10.00"), "ORDER-1"))
                .thenReturn(Map.of("id", 88L));
        Map<String, Object> completed = new LinkedHashMap<>(order);
        completed.put("status", "SUCCESS");
        when(mapper.selectWechatPaymentOrderByStore("ORDER-1", 1L)).thenReturn(order, completed);

        Map<String, Object> result = service.getOrder(1L, "ORDER-1");

        assertThat(result.get("status")).isEqualTo("SUCCESS");
        verify(mainFlowService).applyConfirmedWechatRecharge(
                1L, 99L, new BigDecimal("100.00"), new BigDecimal("10.00"), "ORDER-1");
        verify(mapper).linkWechatPaymentOrderTransaction("ORDER-1", 88L);
    }

    @Test
    void failedAndClosedGatewayStatesBecomeTerminalWithoutWalletMutation() {
        Map<String, Object> order = waitingOrder();
        when(mapper.selectWechatPaymentOrderByStore("ORDER-1", 1L)).thenReturn(order);
        when(mapper.claimWechatPaymentOrderQuery(eq("ORDER-1"), eq(1L), any(), any())).thenReturn(1);
        when(gateway.queryByOutTradeNo("ORDER-1", "1900000002"))
                .thenReturn(payment("PAYERROR", "100.00", "1900000002"));
        when(mapper.selectWechatPaymentOrderForUpdate("ORDER-1")).thenReturn(order);

        service.getOrder(1L, "ORDER-1");

        verify(mapper).updateWechatPaymentOrderTerminal(
                eq("ORDER-1"), eq("FAILED"), any(), eq("PAYERROR"), any(), any());
        verify(mainFlowService, never()).applyConfirmedWechatRecharge(any(), any(), any(), any(), any());
    }

    @Test
    void closedPaymentAndExpiredLocalOrderNeverCreditWallet() {
        Map<String, Object> order = waitingOrder();
        order.put("id", 9L);
        when(mapper.selectWechatPaymentOrderByStore("ORDER-1", 1L)).thenReturn(order);
        when(mapper.claimWechatPaymentOrderQuery(eq("ORDER-1"), eq(1L), any(), any())).thenReturn(1);
        when(gateway.queryByOutTradeNo("ORDER-1", "1900000002"))
                .thenReturn(payment("CLOSED", "100.00", "1900000002"));
        when(mapper.selectWechatPaymentOrderForUpdate("ORDER-1")).thenReturn(order);

        service.getOrder(1L, "ORDER-1");

        verify(mapper).updateWechatPaymentOrderTerminal(
                eq("ORDER-1"), eq("CLOSED"), any(), eq("CLOSED"), any(), any());
        verify(mainFlowService, never()).applyConfirmedWechatRecharge(any(), any(), any(), any(), any());

        when(mapper.selectExpiredWechatPaymentOrders(any(), eq(50))).thenReturn(java.util.List.of(order));
        when(mapper.claimWechatPaymentOrderForClose(9L)).thenReturn(1);
        assertThat(service.closeExpiredOrders()).isEqualTo(1);
        verify(gateway).closeOrder("ORDER-1", "1900000002");
        verify(mapper).updateWechatPaymentOrderTerminal(
                "ORDER-1", "CLOSED", null, "EXPIRED", "支付单已超时关闭", null);
    }

    @Test
    void confirmedRechargeUsesPromisedGiftAndExistingIdempotencyBeforeWalletMutation() {
        MainFlowMapper flowMapper = mock(MainFlowMapper.class);
        MemberNotificationService notifications = mock(MemberNotificationService.class);
        MainFlowService flowService = new MainFlowService(flowMapper, notifications);
        Map<String, Object> existing = Map.of(
                "id", 88L, "storeId", 1L, "memberId", 99L, "type", "RECHARGE");
        when(flowMapper.selectSuccessfulTransactionByIdempotencyKey("wechat-native:ORDER-1"))
                .thenReturn(existing);

        assertThat(flowService.applyConfirmedWechatRecharge(
                1L, 99L, new BigDecimal("100.00"), new BigDecimal("10.00"), "ORDER-1"))
                .isSameAs(existing);
        verify(flowMapper, never()).selectWalletForUpdate(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void confirmedRechargeDoesNotRecalculateGiftAfterTierChanges() {
        MainFlowMapper flowMapper = mock(MainFlowMapper.class);
        MainFlowService flowService = new MainFlowService(flowMapper, mock(MemberNotificationService.class));
        Map<String, Object> wallet = new LinkedHashMap<>();
        wallet.put("memberId", 99L);
        wallet.put("rechargeBalance", new BigDecimal("0.00"));
        wallet.put("giftBalance", new BigDecimal("0.00"));
        wallet.put("accumulatedRecharge", new BigDecimal("0.00"));
        wallet.put("accumulatedGift", new BigDecimal("0.00"));
        wallet.put("accumulatedConsumption", new BigDecimal("0.00"));
        wallet.put("rechargeCount", 0);
        wallet.put("consumptionCount", 0);
        when(flowMapper.selectMemberDetailForUpdate(99L, 1L)).thenReturn(Map.of("id", 99L));
        when(flowMapper.selectWalletForUpdate(99L)).thenReturn(wallet);
        when(flowMapper.selectMemberDetail(99L, null)).thenReturn(Map.of(
                "storeName", "测试门店", "name", "测试会员", "mobile", "13800000000"));
        AtomicReference<Object> insertedGiftAmount = new AtomicReference<>();
        doAnswer(invocation -> {
            Map<String, Object> transaction = invocation.getArgument(0);
            insertedGiftAmount.set(transaction.get("giftAmount"));
            transaction.put("id", 88L);
            return 1;
        }).when(flowMapper).insertTransaction(anyMap());

        flowService.applyConfirmedWechatRecharge(
                1L, 99L, new BigDecimal("100.00"), new BigDecimal("17.00"), "ORDER-1");

        assertThat(insertedGiftAmount.get()).isEqualTo(new BigDecimal("17.00"));
        verify(flowMapper, never()).selectRechargeTiers(any());
    }

    @Test
    void realPaymentLinkedTransactionsRejectReverseAndRefundBeforeWalletMutation() {
        MainFlowMapper flowMapper = mock(MainFlowMapper.class);
        MainFlowService flowService = new MainFlowService(flowMapper, mock(MemberNotificationService.class));
        when(flowMapper.selectTransactionForUpdate(77L, 1L)).thenReturn(Map.of(
                "id", 77L, "store_id", 1L, "member_id", 99L, "type", "RECHARGE", "status", "SUCCESS"));
        when(flowMapper.selectWechatPaymentOrderByWalletTransaction(77L))
                .thenReturn(Map.of("outTradeNo", "ORDER-1"));

        assertThatThrownBy(() -> flowService.reverse(1L, 77L, "MERCHANT", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("微信退款");
        verify(flowMapper, never()).selectWalletForUpdate(any());

        when(flowMapper.selectTransactionForUpdate(78L, 1L)).thenReturn(Map.of(
                "id", 78L, "store_id", 1L, "member_id", 99L, "type", "CONSUMPTION", "status", "SUCCESS"));
        when(flowMapper.selectWechatPaymentOrderByWalletTransaction(78L))
                .thenReturn(Map.of("outTradeNo", "ORDER-2"));
        assertThatThrownBy(() -> flowService.refund(1L, 78L, BigDecimal.ONE, "MERCHANT", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("微信退款");
        verify(flowMapper, never()).selectWalletForUpdate(any());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }

    private void enableRealPayment() {
        when(capabilityService.requireRealPaymentSettings(1L))
                .thenReturn(new RealPaymentSettings("wx-service-provider-app", "1900000001", "1900000002"));
    }

    private Map<String, Object> store() {
        return Map.of("id", 1L, "shopName", "测试门店", "address", "深圳市南山区");
    }

    private Map<String, Object> waitingOrder() {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("outTradeNo", "ORDER-1");
        order.put("storeId", 1L);
        order.put("memberId", 99L);
        order.put("rechargeAmount", new BigDecimal("100.00"));
        order.put("giftAmount", new BigDecimal("10.00"));
        order.put("spMchid", "1900000001");
        order.put("subMchid", "1900000002");
        order.put("appid", "wx-service-provider-app");
        order.put("status", "WAITING_PAYMENT");
        return order;
    }

    private WechatPaymentGateway.GatewayPayment payment(String state, String amount, String subMchid) {
        return new WechatPaymentGateway.GatewayPayment(
                "ORDER-1", "WECHAT-1", "wx-service-provider-app", "1900000001", subMchid,
                state, state, new BigDecimal(amount), "2026-09-22T10:00:00+08:00");
    }

    private WechatPaymentGateway.WechatNotification notification() {
        return new WechatPaymentGateway.WechatNotification(
                "PUB_KEY_ID", "signature", "1700000000", "nonce", "{\"resource\":{}}");
    }

    private WechatPayProperties paymentProperties() {
        WechatPayProperties properties = new WechatPayProperties();
        properties.setMode("WECHAT_SERVICE_PROVIDER");
        properties.getWechat().setSpAppId("wx-service-provider-app");
        properties.getWechat().setSpMchid("1900000001");
        return properties;
    }
}
