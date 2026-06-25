package com.gechuang.stationery.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.PageResult;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
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

    @Test
    void seededMemberDetailShouldSummarizeSeedTransactions() {
        DemoDataStore dataStore = new DemoDataStore();
        DemoDataStore.StoreAccount merchant = loginMerchant(dataStore);
        Long memberId = memberIdByName(dataStore, merchant, "赵天宇");

        PageResult<Map<String, Object>> recharges = dataStore.memberTransactions(merchant.getId(), memberId,
                "RECHARGE", 1, 100);
        PageResult<Map<String, Object>> consumptions = dataStore.memberTransactions(merchant.getId(), memberId,
                "CONSUMPTION", 1, 100);
        Map<String, Object> detail = dataStore.memberDetail(merchant.getId(), memberId, false);

        assertThat(detail.get("rechargeCount")).isEqualTo((int) recharges.getTotal());
        assertThat(detail.get("accumulatedRecharge")).isEqualTo(sumAmount(recharges));
        assertThat(detail.get("consumptionCount")).isEqualTo((int) consumptions.getTotal());
        assertThat(detail.get("accumulatedConsumption")).isEqualTo(sumAmount(consumptions));
    }

    @Test
    void memberListAndLookupShouldExposeFrontendDisplayFields() {
        DemoDataStore dataStore = new DemoDataStore();
        DemoDataStore.StoreAccount merchant = loginMerchant(dataStore);

        Map<String, Object> member = dataStore.merchantMembers(merchant, "赵天宇", 1, 10).getRecords().get(0);
        assertThat(member).containsKeys("id", "memberNo", "name", "mobile", "gender", "age", "avatarUrl",
                "status", "joinedAt", "totalBalance");
        assertThat(member.get("totalBalance")).isEqualTo("535.00");

        Map<String, Object> lookup = dataStore.lookupMember(merchant, "36004");
        assertThat(lookup.get("matchType")).isEqualTo("SUFFIX_SINGLE");
        assertThat(mapValue(lookup, "member")).containsEntry("totalBalance", "535.00");

        Map<String, Object> partialLookup = dataStore.lookupMember(merchant, "137001");
        assertThat(partialLookup.get("matchType")).isEqualTo("SUFFIX_SINGLE");
        assertThat(mapValue(partialLookup, "member"))
                .containsEntry("name", "余额不足会员")
                .containsEntry("mobile", "13700137009");
    }

    @Test
    void demoConsumptionShouldExposeChineseInsufficientBalanceMessage() {
        DemoDataStore dataStore = new DemoDataStore();
        DemoDataStore.StoreAccount merchant = loginMerchant(dataStore);
        Long memberId = memberIdByName(dataStore, merchant, "余额不足会员");

        assertThatThrownBy(() -> dataStore.consume(merchant, Map.of("memberId", memberId, "amount", "1.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("余额不足");
    }

    @Test
    void adminProfileShouldExposeDisplayIdentityFields() {
        DemoDataStore dataStore = new DemoDataStore();
        DemoDataStore.AuthResult auth = dataStore.loginAdmin("superadmin", "Member@123");

        assertThat(auth.data()).containsEntry("displayName", "Admin")
                .containsEntry("role", "SUPER_ADMIN")
                .containsEntry("roleLabel", "系统管理员");
    }

    private DemoDataStore.StoreAccount loginMerchant(DemoDataStore dataStore) {
        DemoDataStore.AuthResult auth = dataStore.loginMerchant("xinyue_store", "123456");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(DemoDataStore.MERCHANT_TOKEN, auth.token()));
        return dataStore.requireMerchant(request);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Map<String, Object> source, String key) {
        return (Map<String, Object>) source.get(key);
    }

    private Long memberIdByName(DemoDataStore dataStore, DemoDataStore.StoreAccount merchant, String name) {
        return dataStore.merchantMembers(merchant, name, 1, 100).getRecords().stream()
                .filter(row -> name.equals(row.get("name")))
                .map(row -> ((Number) row.get("id")).longValue())
                .findFirst()
                .orElseThrow();
    }

    private String sumAmount(PageResult<Map<String, Object>> transactions) {
        return transactions.getRecords().stream()
                .map(row -> new BigDecimal(String.valueOf(row.get("amount"))))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2)
                .toPlainString();
    }
}
