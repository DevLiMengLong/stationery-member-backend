package com.gechuang.stationery.mainflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.notification.MemberNotificationService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MainFlowServiceDashboardTest {

    private final MainFlowMapper mapper = mock(MainFlowMapper.class);
    private final MainFlowService service = new MainFlowService(mapper, mock(MemberNotificationService.class));

    @Test
    void merchantDashboardShouldExposeMonthlyStatsAndStoreEntitlements() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 31L);
        row.put("account", "store-31");
        row.put("shopName", "测试门店");
        row.put("contactMobile", "13800138000");
        row.put("expiresAt", "2027-01-02 12:00:00");
        row.put("smsQuota", 12);
        row.put("totalRechargeAmount", new BigDecimal("300.00"));
        row.put("totalGiftAmount", new BigDecimal("50.00"));
        row.put("pendingConsumptionAmount", new BigDecimal("130.00"));
        row.put("pendingRechargeAmount", new BigDecimal("105.25"));
        row.put("pendingGiftAmount", new BigDecimal("24.75"));
        row.put("todayNewMembers", 0);
        row.put("todayConsumptionAmount", BigDecimal.ZERO);
        row.put("todayRechargeAmount", BigDecimal.ZERO);
        row.put("weekNewMembers", 0);
        row.put("weekConsumptionAmount", BigDecimal.ZERO);
        row.put("weekRechargeAmount", BigDecimal.ZERO);
        row.put("monthNewMembers", 8);
        row.put("monthConsumptionAmount", new BigDecimal("42.00"));
        row.put("monthRechargeAmount", new BigDecimal("70.00"));

        when(mapper.selectMerchantDashboard(eq(31L), any(), any(), any(), any())).thenReturn(row);

        Map<String, Object> dashboard = service.merchantDashboard(Map.of("id", 31L));

        assertThat(dashboard)
                .containsEntry("smsQuota", 12)
                .containsEntry("expiresAt", "2027-01-02 12:00:00")
                .containsEntry("totalRechargeAmount", "300.00")
                .containsEntry("totalGiftAmount", "50.00")
                .containsEntry("pendingConsumptionAmount", "130.00")
                .containsEntry("pendingRechargeAmount", "105.25")
                .containsEntry("pendingGiftAmount", "24.75")
                .containsEntry("month", Map.of(
                        "newMembers", 8,
                        "consumptionAmount", "42.00",
                        "rechargeAmount", "70.00"
                ));
    }
}
