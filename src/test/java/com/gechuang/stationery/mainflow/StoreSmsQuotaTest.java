package com.gechuang.stationery.mainflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.notification.MemberNotificationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StoreSmsQuotaTest {

    private final MainFlowMapper mapper = mock(MainFlowMapper.class);
    private final MainFlowService service = new MainFlowService(mapper, mock(MemberNotificationService.class));

    @Test
    void createStoreShouldStartWithZeroAvailableSms() {
        when(mapper.selectStoreByAccount("new_store")).thenReturn(null);
        when(mapper.insertStore(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> store = invocation.getArgument(0);
            store.put("id", 7L);
            return 1;
        });
        when(mapper.selectStoreById(7L)).thenReturn(store(7L, 0));

        service.createStore(Map.of("account", "new_store"));

        ArgumentCaptor<Map<String, Object>> savedStore = ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertStore(savedStore.capture());
        assertThat(savedStore.getValue()).containsEntry("smsQuota", 0);
    }

    @Test
    void setStoreSmsQuotaShouldSaveAvailableSmsCount() {
        when(mapper.updateStoreSmsQuota(7L, 23)).thenReturn(1);
        when(mapper.selectStoreById(7L)).thenReturn(store(7L, 2));

        service.setStoreSmsQuota(7L, 23);

        verify(mapper).updateStoreSmsQuota(7L, 23);
    }

    @Test
    void settingTheExistingSmsQuotaShouldNotReportTheStoreAsMissing() {
        when(mapper.updateStoreSmsQuota(7L, 2)).thenReturn(0);
        when(mapper.selectStoreById(7L)).thenReturn(store(7L, 2));

        assertThat(service.setStoreSmsQuota(7L, 2)).containsEntry("smsQuota", 2);
    }

    @Test
    void updateStoreShouldNotOverwriteTheConcurrentSmsQuotaBalance() {
        when(mapper.selectStoreById(7L)).thenReturn(store(7L, 2));

        service.updateStore(7L, Map.of("shopName", "修改后的门店"));

        ArgumentCaptor<Map<String, Object>> updatedStore = ArgumentCaptor.forClass(Map.class);
        verify(mapper).updateStore(updatedStore.capture());
        assertThat(updatedStore.getValue()).doesNotContainKey("smsQuota");
    }

    @Test
    void setStoreSmsQuotaShouldRejectNegativeSmsCount() {
        when(mapper.selectStoreById(7L)).thenReturn(store(7L, 2));

        assertThatThrownBy(() -> service.setStoreSmsQuota(7L, -1))
                .hasMessageContaining("短信数量必须为非负整数");

        verify(mapper, never()).updateStoreSmsQuota(7L, -1);
    }

    private Map<String, Object> store(long id, int smsQuota) {
        Map<String, Object> store = new LinkedHashMap<>();
        store.put("id", id);
        store.put("shopName", "测试门店");
        store.put("contactMobile", "13800000000");
        store.put("avatarUrl", "");
        store.put("address", "");
        store.put("status", "ACTIVE");
        store.put("smsQuota", smsQuota);
        return store;
    }
}
