package com.gechuang.stationery.mainflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.notification.MemberNotificationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StoreNearbySchoolsTest {

    private final MainFlowMapper mapper = mock(MainFlowMapper.class);
    private final MainFlowService service = new MainFlowService(mapper, mock(MemberNotificationService.class));

    @Test
    void createStoreShouldPersistAndReturnNearbySchoolNames() {
        String nearbySchools = "东城小学\n实验中学";
        when(mapper.selectStoreByAccount("new_store")).thenReturn(null);
        when(mapper.insertStore(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> store = invocation.getArgument(0);
            store.put("id", 7L);
            return 1;
        });
        when(mapper.selectStoreById(7L)).thenReturn(store(nearbySchools));

        Map<String, Object> created = service.createStore(Map.of(
                "account", "new_store",
                "nearbySchools", nearbySchools));

        ArgumentCaptor<Map<String, Object>> savedStore = ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertStore(savedStore.capture());
        assertThat(savedStore.getValue()).containsEntry("nearbySchools", nearbySchools);
        assertThat(created).containsEntry("nearbySchools", nearbySchools);
    }

    @Test
    void updateStoreShouldSaveAndAllowClearingNearbySchools() {
        when(mapper.selectStoreById(7L)).thenReturn(store("东城小学\n实验中学"));

        service.updateStore(7L, Map.of("nearbySchools", ""));

        ArgumentCaptor<Map<String, Object>> updatedStore = ArgumentCaptor.forClass(Map.class);
        verify(mapper).updateStore(updatedStore.capture());
        assertThat(updatedStore.getValue()).containsEntry("nearbySchools", "");
    }

    @Test
    void updateStoreShouldPreserveNearbySchoolsWhenTheFieldIsOmitted() {
        when(mapper.selectStoreById(7L)).thenReturn(store("东城小学\n实验中学"));

        service.updateStore(7L, Map.of("shopName", "修改后的门店"));

        ArgumentCaptor<Map<String, Object>> updatedStore = ArgumentCaptor.forClass(Map.class);
        verify(mapper).updateStore(updatedStore.capture());
        assertThat(updatedStore.getValue()).containsEntry("nearbySchools", "东城小学\n实验中学");
    }

    private Map<String, Object> store(String nearbySchools) {
        Map<String, Object> store = new LinkedHashMap<>();
        store.put("id", 7L);
        store.put("account", "existing_store");
        store.put("shopName", "现有门店");
        store.put("contactMobile", "13800000000");
        store.put("avatarUrl", "");
        store.put("address", "门店地址");
        store.put("status", "ACTIVE");
        store.put("nearbySchools", nearbySchools);
        return store;
    }
}
