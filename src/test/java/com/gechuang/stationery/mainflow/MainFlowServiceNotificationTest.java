package com.gechuang.stationery.mainflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.notification.MemberNotificationService;
import com.gechuang.stationery.transaction.WalletSnapshot;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MainFlowServiceNotificationTest {

    private final MainFlowMapper mapper = mock(MainFlowMapper.class);
    private final MemberNotificationService notificationService = mock(MemberNotificationService.class);
    private final MainFlowService service = new MainFlowService(mapper, notificationService);

    @Test
    void createMemberShouldNotifyAfterMemberCreated() {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", 99L);
        detail.put("storeId", 1L);
        detail.put("storeName", "晨光文具店");
        detail.put("mobile", "13800000073");
        when(mapper.selectMemberByMobile(1L, "13800000073")).thenReturn(null);
        doAnswer(invocation -> {
            Map<String, Object> member = invocation.getArgument(0);
            member.put("id", 99L);
            return 1;
        }).when(mapper).insertMember(anyMap());
        when(mapper.selectMemberDetail(99L, 1L)).thenReturn(detail);

        service.createMember(1L, Map.of(
                "name", "测试会员",
                "mobile", "13800000073"
        ));

        verify(notificationService).notifyMemberCreated(detail, detail);
    }

    @Test
    void rechargeShouldNotifyAfterTransactionCreated() {
        Map<String, Object> store = Map.of("id", 1L);
        Map<String, Object> wallet = wallet();
        Map<String, Object> transaction = transaction("RECHARGE", "100.00", "10.00", "110.00");
        when(mapper.selectMemberDetailForUpdate(99L, 1L)).thenReturn(Map.of("id", 99L));
        when(mapper.selectWalletForUpdate(99L)).thenReturn(wallet);
        when(mapper.selectRechargeTiers(1L)).thenReturn(List.of(Map.of(
                "tierNo", 1,
                "rechargeAmount", new BigDecimal("100.00"),
                "giftAmount", new BigDecimal("10.00")
        )));
        doAnswer(invocation -> {
            Map<String, Object> tx = invocation.getArgument(0);
            tx.put("id", 10L);
            return 1;
        }).when(mapper).insertTransaction(anyMap());
        when(mapper.selectMemberDetail(eq(99L), any())).thenReturn(Map.of(
                "storeName", "晨光文具店",
                "name", "测试会员",
                "mobile", "13800000073"
        ));

        service.recharge(store, Map.of(
                "memberId", 99L,
                "amount", "100.00"
        ));

        verify(notificationService).notifyRechargeSucceeded(anyMap());
    }

    @Test
    void consumeShouldNotifyAfterTransactionCreated() {
        Map<String, Object> store = Map.of("id", 1L);
        Map<String, Object> wallet = wallet();
        when(mapper.selectMemberDetailForUpdate(99L, 1L)).thenReturn(Map.of("id", 99L));
        when(mapper.selectWalletForUpdate(99L)).thenReturn(wallet);
        doAnswer(invocation -> {
            Map<String, Object> tx = invocation.getArgument(0);
            tx.put("id", 11L);
            return 1;
        }).when(mapper).insertTransaction(anyMap());
        when(mapper.selectMemberDetail(eq(99L), any())).thenReturn(Map.of(
                "storeName", "晨光文具店",
                "name", "测试会员",
                "mobile", "13800000073"
        ));

        service.consume(store, Map.of(
                "memberId", 99L,
                "amount", "10.00"
        ));

        verify(notificationService).notifyConsumptionSucceeded(anyMap());
    }

    private Map<String, Object> wallet() {
        Map<String, Object> wallet = new LinkedHashMap<>();
        wallet.put("memberId", 99L);
        wallet.put("rechargeBalance", new BigDecimal("100.00"));
        wallet.put("giftBalance", new BigDecimal("10.00"));
        wallet.put("accumulatedRecharge", new BigDecimal("0.00"));
        wallet.put("accumulatedGift", new BigDecimal("0.00"));
        wallet.put("accumulatedConsumption", new BigDecimal("0.00"));
        wallet.put("rechargeCount", 0);
        wallet.put("consumptionCount", 0);
        return wallet;
    }

    private Map<String, Object> transaction(String type, String amount, String giftAmount, String afterTotalBalance) {
        Map<String, Object> transaction = new LinkedHashMap<>();
        transaction.put("type", type);
        transaction.put("amount", amount);
        transaction.put("giftAmount", giftAmount);
        transaction.put("afterTotalBalance", afterTotalBalance);
        transaction.put("walletSnapshot", new WalletSnapshot(BigDecimal.ZERO, BigDecimal.ZERO));
        return transaction;
    }
}
