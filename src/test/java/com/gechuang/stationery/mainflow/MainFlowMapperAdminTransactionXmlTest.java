package com.gechuang.stationery.mainflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class MainFlowMapperAdminTransactionXmlTest {

    @Test
    void countAndPageApplyEveryTransactionFilterAndKeepHistory() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/mainflow/MainFlowMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        String namespace = MainFlowMapper.class.getName() + ".";
        Map<String, Object> filters = new HashMap<>();
        filters.put("type", "RECHARGE");
        filters.put("mobile", "138");
        filters.put("name", "李");
        filters.put("storeId", 5L);
        filters.put("startTime", LocalDateTime.of(2026, 10, 1, 0, 0));
        filters.put("endTime", LocalDateTime.of(2026, 10, 10, 23, 59, 59));
        filters.put("amountMin", new BigDecimal("10.00"));
        filters.put("amountMax", new BigDecimal("80.00"));
        filters.put("offset", 20);
        filters.put("limit", 20);

        String countSql = configuration.getMappedStatement(namespace + "countAdminTransactions")
                .getBoundSql(filters).getSql();
        String pageSql = configuration.getMappedStatement(namespace + "selectAdminTransactions")
                .getBoundSql(filters).getSql();

        for (String sql : List.of(countSql, pageSql)) {
            assertThat(sql).contains("tx.type = ?", "m.mobile LIKE CONCAT('%', ?, '%')",
                    "m.name LIKE CONCAT('%', ?, '%')", "tx.store_id = ?",
                    "tx.created_at >= ?", "tx.created_at <= ?",
                    "tx.amount >= ?", "tx.amount <= ?");
            assertThat(sql).doesNotContain("m.deleted_at IS NULL", "m.status != 'DELETED'");
        }
        assertThat(pageSql).contains("ORDER BY tx.created_at DESC, tx.id DESC", "LIMIT ? OFFSET ?");
    }
}
