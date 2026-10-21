package com.gechuang.stationery.mainflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class MainFlowMapperDashboardXmlTest {

    @Test
    void merchantDashboardShouldQueryBothRemainingWalletBalancesForTheCurrentStore() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/mainflow/MainFlowMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        Map<String, Object> params = new HashMap<>();
        params.put("storeId", 31L);
        params.put("todayStart", LocalDateTime.parse("2026-10-21T00:00:00"));
        params.put("tomorrowStart", LocalDateTime.parse("2026-10-22T00:00:00"));
        params.put("weekStart", LocalDateTime.parse("2026-10-19T00:00:00"));
        params.put("monthStart", LocalDateTime.parse("2026-10-01T00:00:00"));
        String sql = configuration.getMappedStatement(MainFlowMapper.class.getName() + ".selectMerchantDashboard")
                .getBoundSql(params).getSql().replaceAll("\\s+", " ");

        assertThat(sql).contains("SELECT SUM(w.recharge_balance) FROM store_member m JOIN member_wallet w ON w.member_id = m.id "
                + "WHERE m.store_id = sa.id AND m.deleted_at IS NULL AND m.status != 'DELETED'");
        assertThat(sql).contains("SELECT SUM(w.gift_balance) FROM store_member m JOIN member_wallet w ON w.member_id = m.id "
                + "WHERE m.store_id = sa.id AND m.deleted_at IS NULL AND m.status != 'DELETED'");
        assertThat(sql).contains("AS pendingRechargeAmount", "AS pendingGiftAmount");
    }
}
