package com.gechuang.stationery.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.gechuang.stationery.common.PageResult;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class DemoDataStorePaginationTest {

    @Test
    void seedDataShouldTriggerPaginationForDemoTables() {
        DemoDataStore dataStore = new DemoDataStore();
        DemoDataStore.StoreAccount merchant = loginMerchant(dataStore);

        PageResult<Map<String, Object>> merchantMembers = dataStore.merchantMembers(merchant, "", 1, 5);
        assertThat(merchantMembers.getTotal()).isGreaterThan(5);

        Long memberId = dataStore.merchantMembers(merchant, "赵天宇", 1, 100).getRecords().stream()
                .filter(row -> "赵天宇".equals(row.get("name")))
                .map(row -> ((Number) row.get("id")).longValue())
                .findFirst()
                .orElseThrow();
        assertThat(dataStore.memberTransactions(merchant.getId(), memberId, "RECHARGE", 1, 5).getTotal())
                .isGreaterThan(5);
        assertThat(dataStore.memberTransactions(merchant.getId(), memberId, "CONSUMPTION", 1, 5).getTotal())
                .isGreaterThan(5);

        assertThat(dataStore.adminStores("", "", 1, 5).getTotal()).isGreaterThan(5);
        assertThat(dataStore.adminMembers("", "", null, null, null, null, null, 1, 5).getTotal())
                .isGreaterThan(5);
        assertThat(dataStore.activityRows()).hasSizeGreaterThan(5);
    }

    private DemoDataStore.StoreAccount loginMerchant(DemoDataStore dataStore) {
        DemoDataStore.AuthResult auth = dataStore.loginMerchant("xinyue_store", "123456");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(DemoDataStore.MERCHANT_TOKEN, auth.token()));
        return dataStore.requireMerchant(request);
    }
}
